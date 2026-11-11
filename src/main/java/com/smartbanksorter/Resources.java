package com.smartbanksorter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class Resources
{
	private Resources()
	{
	}

	/**
	 * @return the text of a data file bundled with the plugin, or "" if it can't be read
	 */
	static String read(String name)
	{
		try (InputStream in = Resources.class.getResourceAsStream(name))
		{
			return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			log.warn("Couldn't read {}", name, e);
			return "";
		}
	}
}
