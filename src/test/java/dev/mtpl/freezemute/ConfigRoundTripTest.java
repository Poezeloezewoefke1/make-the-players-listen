package dev.mtpl.freezemute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every setting, found by asking the class rather than by remembering.
 *
 * <p>Adding a setting means touching three lists that live apart: the field, the line that reads
 * it out of the file, and the line that writes it back. Miss the reader and the setting silently
 * does nothing however the server owner writes it down. Miss the writer and it does work, until
 * the day the file is rewritten - which happens on every start that finds a setting missing - and
 * then it quietly goes back to its default. Neither shows up in a test that names the settings it
 * checks, because whoever forgot the line would forget the test line too.
 *
 * <p>So this one names none of them. It reads the fields off the class, gives every one of them a
 * value that is not its default, and puts the file through the whole round trip twice: once to
 * prove every setting is read, and once more over the file the mod itself wrote to prove every
 * setting is written.
 */
class ConfigRoundTripTest {
	@TempDir
	Path directory;

	@AfterEach
	void restoreTheDefaults() {
		FreezeMuteConfig.load(directory.resolve("thrown-away.json"));
	}

	private static List<Field> settings() {
		List<Field> fields = new ArrayList<>();

		for (Field field : FreezeMuteConfig.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) {
				continue;
			}

			fields.add(field);
		}

		return fields;
	}

	/** Something this setting is definitely not set to already. */
	private static Object somethingElse(Field field, Object was) {
		Class<?> type = field.getType();

		if (type == boolean.class) {
			return !((Boolean) was);
		}

		if (type == int.class) {
			return ((Integer) was) + 7;
		}

		if (type == long.class) {
			return ((Long) was) + 7L;
		}

		if (type == double.class) {
			return ((Double) was) + 1.5D;
		}

		if (type == String.class) {
			return was + "-changed";
		}

		throw new IllegalStateException("no idea what else to make a " + type + " (" + field.getName() + ")");
	}

	@Test
	void everySettingIsBothWrittenAndReadBack() throws Exception {
		FreezeMuteConfig defaults = new FreezeMuteConfig();
		JsonObject written = new JsonObject();
		List<Field> settings = settings();
		assertTrue(settings.size() >= 20, "expected the whole config, found " + settings.size() + " settings");

		for (Field field : settings) {
			Object other = somethingElse(field, field.get(defaults));

			if (other instanceof Boolean value) {
				written.addProperty(field.getName(), value);
			} else if (other instanceof String value) {
				written.addProperty(field.getName(), value);
			} else {
				written.addProperty(field.getName(), (Number) other);
			}
		}

		Path file = directory.resolve("config.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Files.writeString(file, gson.toJson(written), StandardCharsets.UTF_8);

		// Once, to see that every setting in the file was read out of it.
		FreezeMuteConfig.load(file);
		check(settings, defaults, "written by hand and read back");

		// And again over the file the mod itself wrote, to see that every setting went into it.
		// A setting the writer forgets is missing from that file, so this load takes its default.
		FreezeMuteConfig.load(file);
		check(settings, defaults, "written by the mod and read back");
	}

	private void check(List<Field> settings, FreezeMuteConfig defaults, String when) throws Exception {
		FreezeMuteConfig loaded = FreezeMuteConfig.get();

		for (Field field : settings) {
			Object was = field.get(defaults);
			Object now = field.get(loaded);

			assertNotEquals(was, now, field.getName() + " came back as its default, " + when);
			assertEquals(somethingElse(field, was), now, field.getName() + " did not survive being " + when);
		}
	}
}
