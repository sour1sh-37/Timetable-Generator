package com.sies.timetable.parser;

import com.sies.timetable.model.ConstraintSpec;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses constraints.csv into a list of ConstraintSpec records.
 */
public class ConstraintsCsvParser {

    public static List<ConstraintSpec> parse(String filePath) throws Exception {
        List<ConstraintSpec> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine(); // Header
            if (headerLine == null) return list;

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> tokens = parseCsvLine(line);
                if (tokens.size() >= 8) {
                    String id = tokens.get(0).trim();
                    String name = tokens.get(1).trim();
                    String type = tokens.get(2).trim();
                    int defaultWeight = 0;
                    try {
                        defaultWeight = Integer.parseInt(tokens.get(3).trim());
                    } catch (NumberFormatException ignored) {}
                    String sourceDoc = tokens.get(4).trim();
                    String sourceSec = tokens.get(5).trim();
                    String desc = tokens.get(6).trim();
                    String math = tokens.get(7).trim();

                    list.add(new ConstraintSpec(id, name, type, defaultWeight, sourceDoc, sourceSec, desc, math));
                }
            }
        }
        return list;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result;
    }
}
