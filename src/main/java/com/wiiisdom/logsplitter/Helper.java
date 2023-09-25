/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.wiiisdom.logsplitter;

import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFDataFormat;
import org.apache.poi.xssf.usermodel.XSSFRow;

/**
 *
 * @author jean-francois.cayron
 */
public class Helper {

    public static void setInputFile(File InputFile) {
        Helper.InputFile = InputFile;
    }

    public static void setOutputFile(File OutputFile) {
        Helper.OutputFile = OutputFile;
    }

//    static final LogManager LOG_MGR = LogManager.getLogManager();
    static final Logger LOGGER = Logger.getLogger("");

    // Excel Column Headers
    static final String HDR_OPEN_TIME = "ObjOpenDatetime";
    static final String HDR_CLOSE_TIME = "ObjCloseDatetime";
    static final String HDR_INSERT_TIME = "ObjInsertDatetime";
    static final String HDR_ID = "ObjID";
    static final String HDR_PATH = "ObjPath";
    static final String HDR_OBJ_TYPE = "ObjType";
    static final String HDR_FOLDER_TYPE = "FolderType";
    static final String HDR_EXTRACT_DUR = "ExtractionDurSec";
    static final String HDR_INSERT_DUR = "InsertDurSec";
    static final String HDR_NUM_IN_QUEUE = "NumberInQueue";
    static final String HDR_PARAM_NAME = "Parameter";
    static final String HDR_PARAM_VALUE = "Value";

    static final String PATTERN_PARAMETERS = ".*Eyes Parameters : \\[(?<eyesParam>.*?) \\]";
    static Pattern pattern_parameters = Pattern.compile(PATTERN_PARAMETERS, Pattern.CASE_INSENSITIVE);
    static final String PATTERN_OPENING = "^(?<dateTime>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}).*?Opening (?<objType>.*?): (?<objID>\\d*)#(?<objPath>.*?) \\(type:(?<pathType>.*?)\\)";
    static Pattern pattern_opening = Pattern.compile(PATTERN_OPENING);
    static final String PATTERN_CLOSING = "^(?<dateTime>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}).*?Close (?<objType>.*?): ?(?<objID>\\d*) \\[Extraction time : (?<extrTime>\\d*) s\\]";
    static Pattern pattern_closing = Pattern.compile(PATTERN_CLOSING);
    static final String PATTERN_INSERTING = "^(?<dateTime>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}).*?Insert (?<objType>.*?) metadata in Eyes DB: (?<objID>\\d*) \\[Batch insertion time : (?<insertTime>\\d*) s\\]";
    static Pattern pattern_inserting = Pattern.compile(PATTERN_INSERTING);
    static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";
    static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);

// XLSX output
    static XSSFWorkbook workbook;
    static XSSFSheet parameterSheet;
    static XSSFSheet dataSheet;
    static XSSFDataFormat dateFormat;
    static CellStyle dateStyle, integerStyle, numberStyle, generalStyle;
    static int dataRowIndex;

    private static File InputFile = null;
    private static File OutputFile = null;
    private static BufferedReader reader;

    private static boolean hasParameters = false;
    private static final HashMap<Integer, QueuedItem> logObjects = new HashMap<>();
    private static List<PatternEntry> patterns = new ArrayList<>();

    protected static void RunFile() throws FileNotFoundException, IOException {
        Initialize();
        LoadPatterns();

        reader = new BufferedReader(new FileReader(InputFile));
        String InputLine;
        while ((InputLine = reader.readLine()) != null) {
            ProcessLine(InputLine);
        }
        for (Integer key : logObjects.keySet()) {  // Write incomplete entries
            InsertRowMain(key, false);
        }
        for (int iX = 0; iX < workbook.getNumberOfSheets(); iX++) {
            XSSFSheet sheet=workbook.getSheetAt(iX);
            XSSFRow row=sheet.getRow(0);
            int lastCol=row.getLastCellNum()-1;
            for (int iY = 0; iY <= lastCol; iY++) {
               sheet.autoSizeColumn(iY);
               if (sheet.getColumnWidth(iY)>40000) sheet.setColumnWidth(iY, 40000);
            }
            
        }
        try (FileOutputStream out = new FileOutputStream(OutputFile)) {
            workbook.write(out);
        }
    }

    private static void Initialize() {
        Formatter formatter = new MyFormatter();
        Level logLevel = Level.parse((System.getProperty("logLevel", "info")).toUpperCase());
        LOGGER.setLevel(logLevel);
        for (Handler handler : LOGGER.getHandlers()) {
            handler.setLevel(logLevel);
        }
        LOGGER.getHandlers()[0].setFormatter(formatter);
        LOGGER.log(Level.INFO, "Log Level set to {0}", logLevel.toString());
        LOGGER.log(Level.INFO, "Input: {0}", InputFile.getAbsolutePath());
        LOGGER.log(Level.INFO, "Output: {0}", OutputFile.getAbsolutePath());
        workbook = new XSSFWorkbook();
        //
        dateFormat = workbook.createDataFormat();
        dateFormat.putFormat((short) 127, "yyyy-MM-dd HH:mm:ss");
        dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat((short) 127);
        //
        integerStyle = workbook.createCellStyle();
        integerStyle.setDataFormat((short) 3); //  "#,##0"
        //
        numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat((short) 4); // "#,##0.00"
        //
        generalStyle = workbook.createCellStyle();
        generalStyle.setDataFormat((short) 0); // General
        //
        parameterSheet = workbook.createSheet("Parameters");
        dataSheet = workbook.createSheet("Data");
        dataRowIndex = 0;
        XSSFRow row = dataSheet.createRow(dataRowIndex++);
        XSSFCell cell;
        int cellIndex = 0;
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_ID);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_PATH);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_OBJ_TYPE);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_FOLDER_TYPE);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_OPEN_TIME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_CLOSE_TIME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_INSERT_TIME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_EXTRACT_DUR);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_INSERT_DUR);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_NUM_IN_QUEUE);
        row = parameterSheet.createRow(0);
        cellIndex = 0;
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_PARAM_NAME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_PARAM_VALUE);
    }

    private static void LoadPatterns() {
        XSSFWorkbook patternBook;
        XSSFSheet patternSheet;
        LOGGER.fine("Starting Patterns Processing");

        String filePath = System.getProperty("patternFile", "Patterns.xlsx");
        LOGGER.log(Level.INFO, "Patterns file path: {0}", filePath);

        try {
            try (FileInputStream file = new FileInputStream(new File(filePath))) {
                LOGGER.log(Level.FINE, "Opening Patterns file: {0}", filePath);
                
                patternBook = new XSSFWorkbook(file);
                patternSheet = patternBook.getSheetAt(0);
                LOGGER.log(Level.FINE, "Opened Sheet{0}", patternSheet.getSheetName());
                boolean firstRow = true;
                for (Row row : patternSheet) {
                    LOGGER.log(Level.FINE, "Starting Patterns row iterator");
                    int cellIX = 0;
                    //For each row, iterate through all the columns
                    if (firstRow) {
                        firstRow = false;
                        continue;
                    }
                    String sheetName = null;
                    String patternStr = null;
                    Pattern pattern;
                    List<Field> fields = new ArrayList<>();
                    Iterator<Cell> cellIterator = row.cellIterator();
                    LOGGER.log(Level.FINE, "Starting Patterns cell iterator");
                    while (cellIterator.hasNext()) {
                        LOGGER.log(Level.FINE, "Handling pattern cell");
                        Cell cell = cellIterator.next();
                        //Check the cell type and format accordingly
                        CellType type;
                        CellStyle style;
                        String dataType;
                        switch (cellIX) {
                            case 0 ->
                                sheetName = cell.getStringCellValue();
                            case 1 ->
                                patternStr = cell.getStringCellValue();
                            default -> {
                                String value = cell.getStringCellValue();
                                String prefix = value.substring(0, 2);
                                switch (prefix) {
                                    case "I_" -> { // integer
                                        type = CellType.NUMERIC;
                                        dataType = "I";
                                        style = integerStyle;
                                        value = value.substring(2);
                                    }
                                    case "N_" -> {// decimal
                                        type = CellType.NUMERIC;
                                        dataType = "N";
                                        style = numberStyle;
                                        value = value.substring(2);
                                    }
                                    case "D_" -> {// date
                                        type = CellType.NUMERIC;
                                        dataType = "D";
                                        style = dateStyle;
                                        value = value.substring(2);
                                    }
                                    default -> {
                                        type = CellType._NONE;
                                        dataType = "G";
                                        style = generalStyle;
                                    }
                                }
                                fields.add(new Field(value, type, style, dataType));
                            }
                        }
                        cellIX++;
                    }
                    pattern = Pattern.compile(patternStr);
                    PatternEntry patternEntry = new PatternEntry(sheetName, patternStr, pattern, fields);
                    patterns.add(patternEntry);
                }
            }
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Exception processing Patterns file " + filePath + " - " + ex.getMessage(), ex);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Exception processing Patterns file " + filePath + " - " + ex.getMessage(), ex);
        }

    }

    private static void ProcessLine(String InputLine) {
// TODO first line must match some pattern
        LOGGER.log(Level.FINE, InputLine);
        if (!hasParameters) {
            Matcher matcher = pattern_parameters.matcher(InputLine);
            if (matcher.find()) {
                LOGGER.fine(matcher.group("eyesParam"));
                ProcessParams(matcher.group("eyesParam"));
                return;
            }
        }
        Matcher matchOpening = pattern_opening.matcher(InputLine);
        if (matchOpening.find()) {
            ProcessOpening(matchOpening);
            return;
        }
        Matcher matchClosing = pattern_closing.matcher(InputLine);
        if (matchClosing.find()) {
            ProcessClosing(matchClosing);
            return;
        }
        Matcher matchInserting = pattern_inserting.matcher(InputLine);
        if (matchInserting.find()) {
            ProcessInserting(matchInserting);
            return;
        }
        LOGGER.fine("Falling through to other patterns");
        for (PatternEntry patternEntry : patterns) {
            if (CheckOnePattern(patternEntry, InputLine)) {
                break; // if one pattern matches, no need to chack others
            }
        }

        LOGGER.fine("Falling through - nothing found");
    }

    private static void ProcessParams(String allParams) {
        LOGGER.fine("Starting Parameters Processing");
        XSSFRow row = parameterSheet.createRow(0);
        XSSFCell cell = row.createCell(0);
        cell.setCellValue(HDR_PARAM_NAME);
        cell = row.createCell(1);
        cell.setCellValue(HDR_PARAM_VALUE);

        hasParameters = true;
        String param[] = allParams.split(" -", -1);
        int rowIndex = 0;
        for (String oneParam : param) {
            LOGGER.fine(oneParam);
            String both[] = oneParam.split("=", 2);
            if (both.length > 1) {
                rowIndex++;
                LOGGER.log(Level.FINE, "{0} - {1}", new Object[]{both[0], both[1]});
                row = parameterSheet.createRow(rowIndex);
                cell = row.createCell(0);
                cell.setCellValue(both[0]);
                cell = row.createCell(1);
                cell.setCellValue(both[1]);
            }
        }
    }

    private static void ProcessOpening(Matcher matchOpening) {
        LOGGER.fine("Starting OPENING Processing");
        LOGGER.fine(LocalDateTime.parse(matchOpening.group("dateTime"), dateFormatter).toString());
        LOGGER.fine(matchOpening.group("objType"));
        LOGGER.fine(matchOpening.group("objID"));
        LOGGER.fine(matchOpening.group("objPath"));
        LOGGER.fine(matchOpening.group("pathType"));
        QueuedItem logObject = new QueuedItem(
                Integer.valueOf(matchOpening.group("objID")),
                matchOpening.group("objType"),
                matchOpening.group("objPath"),
                matchOpening.group("pathType"),
                LocalDateTime.parse(matchOpening.group("dateTime"), dateFormatter),
                logObjects.size() + 1
        );
        logObjects.put(logObject.getObjectID(), logObject);
    }

    private static void ProcessClosing(Matcher matchClosing) {
        LOGGER.fine("Starting CLOSING Processing");
        LOGGER.fine(LocalDateTime.parse(matchClosing.group("dateTime"), dateFormatter).toString());
        LOGGER.fine(matchClosing.group("objType"));
        LOGGER.fine(matchClosing.group("objID"));
        LOGGER.fine(matchClosing.group("extrTime"));
        QueuedItem queuedItem = logObjects.get(Integer.valueOf(matchClosing.group("objID")));
        if (queuedItem != null) {
            queuedItem.setCloseTime(LocalDateTime.parse(matchClosing.group("dateTime"), dateFormatter));
            queuedItem.setExtractionDuration(Integer.parseInt(matchClosing.group("extrTime")));
        } else {
            queuedItem = new QueuedItem(Integer.valueOf(matchClosing.group("objID")),
                    matchClosing.group("objType"),
                    LocalDateTime.MIN,
                    LocalDateTime.parse(matchClosing.group("dateTime"), dateFormatter),
                    LocalDateTime.MIN,
                    Integer.valueOf(matchClosing.group("extrTime")),
                    -1,
                    logObjects.size()
            );
            LOGGER.log(Level.WARNING, "No Match found for Closing Object_ID {0}", matchClosing.group("objID"));
        }
        logObjects.put(Integer.valueOf(matchClosing.group("objID")), queuedItem);
    }

    private static void ProcessInserting(Matcher matchInserting) {
        LOGGER.fine("Starting INSERTING Processing");
        LOGGER.fine(LocalDateTime.parse(matchInserting.group("dateTime"), dateFormatter).toString());
        LOGGER.fine(matchInserting.group("objType"));
        LOGGER.fine(matchInserting.group("objID"));
        LOGGER.fine(matchInserting.group("insertTime"));
        QueuedItem logObject = logObjects.get(Integer.valueOf(matchInserting.group("objID")));
        if (logObject != null) {
            logObject.setInsertTime(LocalDateTime.parse(matchInserting.group("dateTime"), dateFormatter));
            logObject.setInsertionDuration(Integer.parseInt(matchInserting.group("insertTime")));
        } else {
            logObject = new QueuedItem(Integer.valueOf(matchInserting.group("objID")),
                    matchInserting.group("objType"),
                    LocalDateTime.MIN,
                    LocalDateTime.parse(matchInserting.group("dateTime"), dateFormatter),
                    LocalDateTime.MIN,
                    Integer.valueOf(matchInserting.group("insertTime")),
                    -1,
                    logObjects.size());
            LOGGER.log(Level.WARNING, "No Match found for Inserting Object_ID {0}", matchInserting.group("objID"));
        }
        logObjects.put(Integer.valueOf(matchInserting.group("objID")), logObject);
        InsertRowMain(Integer.valueOf(matchInserting.group("objID")), true);
    }

    private static void InsertRowMain(Integer objID, boolean delEntry) {
        QueuedItem logObject = logObjects.get(objID);
        XSSFRow row = dataSheet.createRow(dataRowIndex++);
        XSSFCell cell;
        int cellIndex = 0;
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellStyle(integerStyle);
        cell.setCellValue(logObject.getObjectID());
        cell = row.createCell(cellIndex++, CellType.STRING);
        cell.setCellValue(logObject.getObjectPath());
        cell = row.createCell(cellIndex++, CellType.STRING);
        cell.setCellValue(logObject.getObjectType());
        cell = row.createCell(cellIndex++, CellType.STRING);
        cell.setCellValue(logObject.getFolderType());
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getOpenTime());
        cell.setCellStyle(dateStyle);
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getCloseTime());
        cell.setCellStyle(dateStyle);
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getInsertTime());
        cell.setCellStyle(dateStyle);
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getExtractionDuration());
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellStyle(integerStyle);
        cell.setCellValue(logObject.getInsertionDuration());
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellStyle(integerStyle);
        cell.setCellValue(logObject.getNumberInQueue());
        if (delEntry) {
            logObjects.remove(objID);
        }
    }

    private static boolean CheckOnePattern(PatternEntry patternEntry, String inputLine) {
        Matcher matcher = patternEntry.getPattern().matcher(inputLine);
        if (!matcher.find()) {
            return false;
        }
        XSSFSheet sheet;
        if (patternEntry.isIsFound()) {
            sheet = workbook.getSheet(patternEntry.getSheetName());
        } else {
            patternEntry.setIsFound(true);
            sheet = workbook.createSheet(patternEntry.getSheetName());
            XSSFRow headerRow = sheet.createRow(0);
            int iX = 0;
            for (Field header : patternEntry.getFields()) {
                headerRow.createCell(iX, CellType.STRING).setCellValue(header.getName());
                iX++;
            }
        }
        XSSFRow row = sheet.createRow(sheet.getLastRowNum() + 1);
        List< Field> fields = patternEntry.getFields();
        for (int iX = 1; iX <= matcher.groupCount(); iX++) {
            Field field = fields.get(iX - 1);
            Cell cell = row.createCell(iX - 1, field.getType());
            String value = matcher.group(iX);
            switch (field.getDataType()) {
                case "D" ->
                    cell.setCellValue(LocalDateTime.parse(value, dateFormatter));
                case "N" ->
                    cell.setCellValue(Double.parseDouble(value));
                case "I" ->
                    cell.setCellValue(Integer.parseInt(value));
                default ->
                    cell.setCellValue(value);
            }
            cell.setCellStyle(fields.get(iX - 1).getStyle());
        }
        return true;
    }

    private static class MyFormatter extends Formatter {

        // Create a DateFormat to format the LOGGER timestamp.
        //
        private static final DateFormat DF = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss.SSS");

        @Override
        public String format(LogRecord record) {
            StringBuilder builder = new StringBuilder(1000);
            builder.append(DF.format(new Date(record.getMillis()))).append(" - ");
            builder.append("[").append(record.getLevel()).append("] - ");
            builder.append(formatMessage(record));
            builder.append("\n");
            return builder.toString();
        }
    }

    private static class QueuedItem {

        private final Integer objectID;
        private final String objectType;
        private final String objectPath;
        private final String folderType;
        private final LocalDateTime openTime;
        private LocalDateTime closeTime;
        private LocalDateTime insertTime;
        private Integer extractionDuration;
        private Integer insertionDuration;
        private final Integer numberInQueue;

        public QueuedItem(Integer objectID, String objectType, String objectPath, String folderType, LocalDateTime openTime, Integer numberInQueue) {
            this.objectID = objectID;
            this.objectType = objectType;
            this.objectPath = objectPath;
            this.folderType = folderType;
            this.openTime = openTime;
            this.closeTime = LocalDateTime.MIN;
            this.insertTime = LocalDateTime.MIN;
            this.extractionDuration = -1;
            this.insertionDuration = -1;
            this.numberInQueue = numberInQueue;
        }

        public QueuedItem(Integer objectID, String objectType, LocalDateTime openTime, LocalDateTime closeTime, LocalDateTime insertTime, Integer extractionDuration, Integer insertionDuration, Integer numberInQueue) {
            this.objectID = objectID;
            this.objectType = objectType;
            this.objectPath = "N/A";
            this.folderType = "N/A";
            this.openTime = openTime;
            this.closeTime = closeTime;
            this.insertTime = insertTime;
            this.extractionDuration = extractionDuration;
            this.insertionDuration = insertionDuration;
            this.numberInQueue = numberInQueue;
        }

        public Integer getNumberInQueue() {
            return numberInQueue;
        }

        public LocalDateTime getCloseTime() {
            return closeTime;
        }

        public void setCloseTime(LocalDateTime closeTime) {
            this.closeTime = closeTime;
        }

        public LocalDateTime getInsertTime() {
            return insertTime;
        }

        public void setInsertTime(LocalDateTime insertTime) {
            this.insertTime = insertTime;
        }

        public int getExtractionDuration() {
            return extractionDuration;
        }

        public void setExtractionDuration(int extractionDuration) {
            this.extractionDuration = extractionDuration;
        }

        public int getInsertionDuration() {
            return insertionDuration;
        }

        public void setInsertionDuration(int insertionDuration) {
            this.insertionDuration = insertionDuration;
        }

        public Integer getObjectID() {
            return objectID;
        }

        public String getObjectType() {
            return objectType;
        }

        public String getObjectPath() {
            return objectPath;
        }

        public String getFolderType() {
            return folderType;
        }

        public LocalDateTime getOpenTime() {
            return openTime;
        }
    }

    private static class PatternEntry {

        private final String sheetName;
        private final String patternStr;
        private final Pattern pattern;
        private final List< Field> fields;
        private boolean isFound;

        public PatternEntry(String sheetName, String patternStr, Pattern pattern, List< Field> fields) {
            this.sheetName = sheetName;
            this.patternStr = patternStr;
            this.fields = fields;
            this.pattern = pattern;
            this.isFound = false;
        }

        public void setIsFound(boolean isFound) {
            this.isFound = isFound;
        }

        public String getSheetName() {
            return sheetName;
        }

        public String getPatternStr() {
            return patternStr;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public List<Field> getFields() {
            return fields;
        }

        public boolean isIsFound() {
            return isFound;
        }

    }

    private static class Field {

        private final String name;
        private final CellType type;
        private final CellStyle style;
        private final String dataType;

        public Field(String name, CellType type, CellStyle style, String dataType) {
            this.name = name;
            this.type = type;
            this.style = style;
            this.dataType = dataType;
        }

        public String getName() {
            return name;
        }

        public CellType getType() {
            return type;
        }

        public CellStyle getStyle() {
            return style;
        }

        public String getDataType() {
            return dataType;
        }
    }
}