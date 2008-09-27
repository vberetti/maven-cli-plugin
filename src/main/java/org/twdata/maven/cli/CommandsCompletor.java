package org.twdata.maven.cli;

import java.lang.annotation.Inherited;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jline.Completor;

public class CommandsCompletor implements Completor {

	private final List<String> availableCommands = new ArrayList<String>();

	public CommandsCompletor(Collection<String> commands) {
		availableCommands.addAll(commands);
		Collections.sort(availableCommands, String.CASE_INSENSITIVE_ORDER);
	}

	public int complete(String buffer, int cursor, List candidates) {
		for (String availableCommand : availableCommands) {
			if (availableCommand.startsWith(buffer)) {
				candidates.add(availableCommand);
			}
		}
		return cursor - buffer.length();
	}

}
