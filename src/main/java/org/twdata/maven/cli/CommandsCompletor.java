package org.twdata.maven.cli;

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

    /**
     * this method tries to match :
     * <ul>
     * <li>the full buffer</li>
     * <li>the last token</li>
     * </ul>
     */
    public int complete(String buffer, int cursor, List candidates) {
        String completionToken = buffer;

        String[] tokens = buffer.split(" ");
        String lastToken = null;
        if (tokens.length > 0) {
            lastToken = tokens[tokens.length - 1];
        }

        for (String availableCommand : availableCommands) {
            if (availableCommand.startsWith(buffer)) {
                candidates.add(availableCommand);
            }
            if (lastToken != null && availableCommand.startsWith(lastToken)) {
                completionToken = lastToken;
                candidates.add(availableCommand);
            }
        }

        return cursor - completionToken.length();
    }

}
