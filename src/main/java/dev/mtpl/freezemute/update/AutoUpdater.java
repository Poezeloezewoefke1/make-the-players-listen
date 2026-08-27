package dev.mtpl.freezemute.update;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.mtpl.freezemute.FreezeMute;
import dev.mtpl.freezemute.FreezeMuteConfig;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Keeps the mod up to date on its own.
 *
 * <p>On every server start this asks GitHub for the latest release of the configured repository.
 * If it is newer than what is running, the jar is downloaded, checked, and put into the mods
 * folder in place of the running one. Fabric decides what to load before any mod code runs, so
 * the new version is what starts the <em>next</em> time the server boots - which for a server
 * that restarts on a schedule means it keeps itself current with nothing to do by hand.
 *
 * <p>The one thing that must never happen is leaving two jars with the same mod id in the folder,
 * because Fabric refuses to start at all in that state. So the old jar is only removed once the
 * new one is downloaded and verified, and if it cannot be removed the download is thrown away and
 * the server is left exactly as it was.
 *
 * <p>This does mean the server runs whatever that repository publishes. It is switched off with
 * {@code autoUpdate} in the config, and {@code updateRepository} pins which repository is asked -
 * nothing follows a redirect to a different host.
 */
public final class AutoUpdater {
	private static final String API = "https://api.github.com/repos/";
	private static final long MAX_DOWNLOAD_BYTES = 32L * 1024 * 1024;
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private AutoUpdater() {
	}

	/** Starts the check on a daemon thread, so a slow or unreachable GitHub never delays boot. */
	public static void start() {
		FreezeMuteConfig config = FreezeMuteConfig.get();

		if (!config.autoUpdate) {
			FreezeMute.LOGGER.info("Auto update is off");
			return;
		}

		Thread thread = new Thread(() -> run(config), "freezemute-auto-update");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run(FreezeMuteConfig config) {
		try {
			check(config);
		} catch (Exception exception) {
			// An update is a convenience; it must never take the server down with it.
			FreezeMute.LOGGER.warn("Auto update failed ({}) - the server keeps running the installed version",
					exception.toString());
		}
	}

	private static void check(FreezeMuteConfig config) throws Exception {
		String repository = config.updateRepository;

		if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
			FreezeMute.LOGGER.warn("updateRepository '{}' does not look like owner/repo, skipping the update check",
					repository);
			return;
		}

		String running = runningVersion();
		JsonObject release = latestRelease(repository);

		if (release == null) {
			return;
		}

		String latest = stripLeadingV(string(release, "tag_name"));

		if (latest.isEmpty()) {
			FreezeMute.LOGGER.warn("The latest release of {} has no tag, skipping", repository);
			return;
		}

		if (Versions.compare(latest, running) <= 0) {
			FreezeMute.LOGGER.info("Auto update: running {}, latest is {} - nothing to do", running, latest);
			return;
		}

		FreezeMute.LOGGER.info("Auto update: {} is available, running {}", latest, running);

		String assetUrl = modJarAsset(release);

		if (assetUrl == null) {
			FreezeMute.LOGGER.warn("Release {} has no mod jar attached, skipping", latest);
			return;
		}

		if (config.updateCheckOnly) {
			FreezeMute.LOGGER.info("updateCheckOnly is on - download it yourself from {}", assetUrl);
			return;
		}

		install(assetUrl, latest);
	}

	private static void install(String assetUrl, String version) throws Exception {
		Path current = runningJar();

		if (current == null) {
			FreezeMute.LOGGER.warn("Auto update: could not work out which jar is running, skipping");
			return;
		}

		Path modsFolder = current.getParent();

		if (modsFolder == null || !Files.isWritable(modsFolder)) {
			FreezeMute.LOGGER.warn("Auto update: {} is not writable, skipping", modsFolder);
			return;
		}

		Path download = modsFolder.resolve(FreezeMute.MOD_ID + "-" + version + ".jar.part");
		Files.deleteIfExists(download);

		if (!download(assetUrl, download)) {
			Files.deleteIfExists(download);
			return;
		}

		if (!isOurMod(download)) {
			FreezeMute.LOGGER.warn("Auto update: the downloaded file is not a {} jar, throwing it away",
					FreezeMute.MOD_ID);
			Files.deleteIfExists(download);
			return;
		}

		// Only now, with a verified jar on disk, is the running one allowed to go. If it cannot
		// be moved out of the way the download is deleted: two jars with one mod id would stop
		// the server from starting at all, which is far worse than being a version behind.
		if (!retire(current)) {
			FreezeMute.LOGGER.warn("Auto update: could not remove {}, so the update was thrown away. "
					+ "Replace it by hand to update.", current.getFileName());
			Files.deleteIfExists(download);
			return;
		}

		Path target = modsFolder.resolve(FreezeMute.MOD_ID + "-" + version + ".jar");
		Files.move(download, target, StandardCopyOption.REPLACE_EXISTING);

		FreezeMute.LOGGER.info("Auto update: installed {} as {} - it starts on the next server restart",
				version, target.getFileName());
	}

	/** Deletes the running jar, or failing that renames it so Fabric stops loading it. */
	private static boolean retire(Path jar) {
		try {
			Files.delete(jar);
			return true;
		} catch (Exception deleteFailed) {
			// Windows will not delete a jar that is open. A rename it usually allows, and Fabric
			// only loads files ending in .jar, so the old one is out of the way either way.
			try {
				Files.move(jar, jar.resolveSibling(jar.getFileName() + ".old"),
						StandardCopyOption.REPLACE_EXISTING);
				return true;
			} catch (Exception moveFailed) {
				return false;
			}
		}
	}

	private static boolean download(String url, Path target) throws Exception {
		HttpResponse<InputStream> response = send(url, HttpResponse.BodyHandlers.ofInputStream());

		if (response.statusCode() != 200) {
			FreezeMute.LOGGER.warn("Auto update: downloading returned HTTP {}", response.statusCode());
			response.body().close();
			return false;
		}

		long written;

		try (InputStream input = response.body()) {
			written = Files.copy(limited(input), target, StandardCopyOption.REPLACE_EXISTING);
		}

		if (written <= 0) {
			FreezeMute.LOGGER.warn("Auto update: the download was empty");
			return false;
		}

		return true;
	}

	/** Refuses to write more than {@link #MAX_DOWNLOAD_BYTES}, so a bad URL cannot fill the disk. */
	private static InputStream limited(InputStream input) {
		return new InputStream() {
			private long read;

			@Override
			public int read() throws java.io.IOException {
				int value = input.read();

				if (value >= 0 && ++read > MAX_DOWNLOAD_BYTES) {
					throw new java.io.IOException("the download is larger than " + MAX_DOWNLOAD_BYTES + " bytes");
				}

				return value;
			}

			@Override
			public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
				int count = input.read(buffer, offset, length);

				if (count > 0 && (read += count) > MAX_DOWNLOAD_BYTES) {
					throw new java.io.IOException("the download is larger than " + MAX_DOWNLOAD_BYTES + " bytes");
				}

				return count;
			}
		};
	}

	/** A downloaded file is only swapped in once it really is a jar of this mod. */
	private static boolean isOurMod(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric.mod.json");

			if (entry == null) {
				return false;
			}

			try (InputStream input = zip.getInputStream(entry)) {
				JsonElement root = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));

				if (!root.isJsonObject()) {
					return false;
				}

				return FreezeMute.MOD_ID.equals(string(root.getAsJsonObject(), "id"));
			}
		} catch (Exception exception) {
			return false;
		}
	}

	private static JsonObject latestRelease(String repository) throws Exception {
		HttpResponse<String> response = send(API + repository + "/releases/latest",
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (response.statusCode() == 404) {
			FreezeMute.LOGGER.info("Auto update: {} has no releases yet", repository);
			return null;
		}

		if (response.statusCode() != 200) {
			FreezeMute.LOGGER.warn("Auto update: GitHub returned HTTP {}", response.statusCode());
			return null;
		}

		JsonElement root = JsonParser.parseString(response.body());
		return root.isJsonObject() ? root.getAsJsonObject() : null;
	}

	/** The release's mod jar - never the sources jar, which is not loadable and crashes servers. */
	private static String modJarAsset(JsonObject release) {
		JsonArray assets = release.getAsJsonArray("assets");

		if (assets == null) {
			return null;
		}

		for (JsonElement element : assets) {
			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject asset = element.getAsJsonObject();
			String name = string(asset, "name");

			if (name.endsWith(".jar") && !name.endsWith("-sources.jar") && !name.endsWith("-dev.jar")) {
				return string(asset, "browser_download_url");
			}
		}

		return null;
	}

	private static <T> HttpResponse<T> send(String url, HttpResponse.BodyHandler<T> handler) throws Exception {
		URI uri = URI.create(url);

		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException("refusing to fetch a non-https url: " + url);
		}

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(TIMEOUT)
				.build();

		HttpRequest request = HttpRequest.newBuilder(uri)
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", FreezeMute.MOD_ID + "-auto-update")
				.timeout(TIMEOUT)
				.GET()
				.build();

		return client.send(request, handler);
	}

	/** The version Fabric says is running, which is the one baked into the loaded jar. */
	public static String runningVersion() {
		return container()
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0");
	}

	private static Path runningJar() {
		return container()
				.map(container -> container.getOrigin().getPaths())
				.filter(paths -> !paths.isEmpty())
				.map(paths -> paths.get(0))
				.filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".jar"))
				.orElse(null);
	}

	private static Optional<ModContainer> container() {
		try {
			return FabricLoader.getInstance().getModContainer(FreezeMute.MOD_ID);
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
	}

	private static String stripLeadingV(String tag) {
		return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
	}

	private static String string(JsonObject object, String key) {
		try {
			return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
		} catch (RuntimeException exception) {
			return "";
		}
	}
}
