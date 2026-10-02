package com.sies.timetable.parser;

import org.apache.poi.ss.usermodel.*;

public class ExcelParserHelper {

    public static String getStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                } else {
                    yield String.valueOf(val);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        double val = cell.getNumericCellValue();
                        if (val == Math.floor(val)) {
                            yield String.valueOf((long) val);
                        } else {
                            yield String.valueOf(val);
                        }
                    } catch (Exception ex) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    public static Integer getIntValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) Math.round(cell.getNumericCellValue());
            case STRING -> {
                String str = cell.getStringCellValue().trim();
                try {
                    yield (int) Math.round(Double.parseDouble(str));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case FORMULA -> {
                try {
                    yield (int) Math.round(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield (int) Math.round(Double.parseDouble(cell.getStringCellValue().trim()));
                    } catch (Exception ex) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }
}
