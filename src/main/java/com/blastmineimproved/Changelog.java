package com.blastmineimproved;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Player-facing notes per plugin version. {@link #VERSION} must match
 * {@code version} in {@code build.gradle} and {@code runelite-plugin.properties}.
 * Append a {@link Release} on each bump so skipped Hub versions still get their notes.
 */
final class Changelog
{
	static final String VERSION = "1.1.1";

	static final List<Release> RELEASES = List.of(
		new Release("1.1.0",
			"Rotation method dropdown: keep loot-as-you-go, or blast every dynamite then loot.",
			"Guide ore pickups can be turned off if you area-loot another way.",
			"Dynamite per trip is configurable; placeholder filler is calculated from that (20→5, 21→4).",
			"The last leftover pot now lights when dynamite runs out, including an odd 21st."
		),
		new Release("1.1.1",
			"Renamed from Blast Mine Improved to Jam's Blast Mine in the plugin panel and Hub."
		)
	);

	private Changelog()
	{
	}

	static boolean isUnseen(String seenVersion)
	{
		return !unseenSince(seenVersion).isEmpty();
	}

	static List<Release> unseenSince(String seenVersion)
	{
		String seen = seenVersion == null ? "" : seenVersion;
		List<Release> unseen = new ArrayList<>();
		for (Release release : RELEASES)
		{
			if (compareVersions(release.version, seen) > 0)
			{
				unseen.add(release);
			}
		}
		return Collections.unmodifiableList(unseen);
	}

	static int compareVersions(String left, String right)
	{
		int[] a = parseVersion(left);
		int[] b = parseVersion(right);
		int n = Math.max(a.length, b.length);
		for (int i = 0; i < n; i++)
		{
			int av = i < a.length ? a[i] : 0;
			int bv = i < b.length ? b[i] : 0;
			if (av != bv)
			{
				return Integer.compare(av, bv);
			}
		}
		return 0;
	}

	private static int[] parseVersion(String version)
	{
		if (version == null || version.isEmpty())
		{
			return new int[0];
		}
		String[] parts = version.split("\\.");
		int[] values = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			try
			{
				values[i] = Integer.parseInt(parts[i]);
			}
			catch (NumberFormatException ex)
			{
				values[i] = 0;
			}
		}
		return values;
	}

	static final class Release
	{
		final String version;
		final List<String> notes;

		Release(String version, String... notes)
		{
			this.version = version;
			this.notes = List.of(notes);
		}
	}
}
