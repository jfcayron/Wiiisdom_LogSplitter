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
import java.time.temporal.ChronoUnit;
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
    static final String HDR_DELTA_CAUSE = "ObjDeltaCause";
    static final String HDR_DELTA_TIME = "ObjDeltaDatetime";
    static final String HDR_OPEN_TIME = "ObjOpenDatetime";
    static final String HDR_CLOSE_TIME = "ObjCloseDatetime";
    static final String HDR_INSERT_TIME = "ObjInsertDatetime";
    static final String HDR_ID = "ObjID";
    static final String HDR_PATH = "ObjPath";
    static final String HDR_OBJ_TYPE = "ObjType";
    static final String HDR_FOLDER_TYPE = "FolderType";
    static final String HDR_DELTA_DUR = "DeltaDurSec";
    static final String HDR_EXTRACT_DUR = "ExtractionDurSec";
    static final String HDR_INSERT_DUR = "InsertDurSec";
    static final String HDR_NUM_IN_QUEUE = "NumberInQueue";
    static final String HDR_POOL = "Pool";
    static final String HDR_THREAD = "Thread";
    static final String HDR_PARAM_NAME = "Parameter";
    static final String HDR_PARAM_VALUE = "Value";

    static final String PATTERN_PARAMETERS = ".*Eyes Parameters : \\[(?<eyesParam>.*?) \\]";
    static Pattern pattern_parameters = Pattern.compile(PATTERN_PARAMETERS, Pattern.CASE_INSENSITIVE);
    static final String PATTERN_DELTA_REASON = "^(?<dateTime>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[pool-(?<pool>\\d*?)-thread-(?<thread>\\d*?)].*?Extraction of the object (?<objID>\\d*) because it('s a| has been) (?<cause>.*?) .*?";
    static Pattern pattern_delta_reason = Pattern.compile(PATTERN_DELTA_REASON, Pattern.CASE_INSENSITIVE);
    static final String PATTERN_OPENING = "^(?<dateTime>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[pool-(?<pool>\\d*?)-thread-(?<thread>\\d*?)].*?Opening (?<objType>.*?): (?<objID>\\d*)#(?<objPath>.*?) \\(type:(?<pathType>.*?)\\)";
    static Pattern pattern_opening = Pattern.compile(PATTERN_OPENING);
    static final String PATTERN_CLOSING = "^(?<dateTime>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[pool-(?<pool>\\d*?)-thread-(?<thread>\\d*?)].*?Close (?<objType>.*?): ?(?<objID>\\d*) \\[Extraction time : (?<extrTime>\\d*) s\\]";
    static Pattern pattern_closing = Pattern.compile(PATTERN_CLOSING);
    static final String PATTERN_INSERTING = "^(?<dateTime>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[pool-(?<pool>\\d*?)-thread-(?<thread>\\d*?)].*?Insert (?<objType>.*?) metadata in Eyes DB: (?<objID>\\d*) \\[Batch insertion time : (?<insertTime>\\d*) s\\]";
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
    private static final List<PatternEntry> patterns = new ArrayList<>();

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
            XSSFSheet sheet = workbook.getSheetAt(iX);
            XSSFRow row = sheet.getRow(0);
            int lastCol = row.getLastCellNum() - 1;
            for (int iY = 0; iY <= lastCol; iY++) {
                sheet.autoSizeColumn(iY);
                if (sheet.getColumnWidth(iY) > 40000) {
                    sheet.setColumnWidth(iY, 40000);
                }
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
        cell.setCellValue(HDR_DELTA_CAUSE);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_DELTA_TIME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_OPEN_TIME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_CLOSE_TIME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_INSERT_TIME);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_DELTA_DUR);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_EXTRACT_DUR);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_INSERT_DUR);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_NUM_IN_QUEUE);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_POOL);
        cell = row.createCell(cellIndex++);
        cell.setCellValue(HDR_THREAD);
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
                LOGGER.log(Level.FINE, "Opened Sheet {0}", patternSheet.getSheetName());
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
                        if (cell.getStringCellValue().isEmpty()) {
                            LOGGER.log(Level.FINE, "Blank Cell: {0}", cell.getStringCellValue());
                            continue;
                        }
                        //Check the cell type and format accordingly
                        CellType type;
                        CellStyle style;
                        String dataType;
                        switch (cellIX) {
                            case 0:
                                sheetName = cell.getStringCellValue();
                                LOGGER.log(Level.FINE, "Sheet Name: {0}", sheetName);
                                break;
                            case 1:
                                patternStr = cell.getStringCellValue();
                                LOGGER.log(Level.FINE, "Pattern: {0}", patternStr);
                                break;
                            default: {
                                String value = cell.getStringCellValue();
                                LOGGER.log(Level.FINE, "Column Name: {0}", value);
                                String prefix = value.substring(0, 2);
                                switch (prefix) {
                                    case "I_": { // integer
                                        type = CellType.NUMERIC;
                                        dataType = "I";
                                        style = integerStyle;
                                        value = value.substring(2);
                                    }
                                    break;
                                    case "N_": {// decimal
                                        type = CellType.NUMERIC;
                                        dataType = "N";
                                        style = numberStyle;
                                        value = value.substring(2);
                                    }
                                    break;
                                    case "D_": {// date
                                        type = CellType.NUMERIC;
                                        dataType = "D";
                                        style = dateStyle;
                                        value = value.substring(2);
                                    }
                                    break;
                                    default: {
                                        type = CellType.STRING;
                                        dataType = "G";
                                        style = generalStyle;
                                    }
                                }
                                fields.add(new Field(value, type, style, dataType));
                                LOGGER.log(Level.FINE, "Data Type: {0}", dataType);

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
        Matcher matchDelta = pattern_delta_reason.matcher(InputLine);
        if (matchDelta.find()) {
            ProcessDelta(matchDelta);
            return;
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

    private static void ProcessDelta(Matcher matchDelta) {
        LOGGER.fine("Starting DELTA Processing");
        LOGGER.fine(LocalDateTime.parse(matchDelta.group("dateTime"), dateFormatter).toString());
        LOGGER.fine(matchDelta.group("objID"));
        LOGGER.fine(matchDelta.group("cause"));
        QueuedItem logObject = new QueuedItem(
                Integer.valueOf(matchDelta.group("objID")),
                "",
                "",
                "",
                matchDelta.group("cause"),
                LocalDateTime.parse(matchDelta.group("dateTime"), dateFormatter),
                LocalDateTime.MIN,
                LocalDateTime.MIN,
                LocalDateTime.MIN,
                -1,
                -1,
                logObjects.size() + 1,
                Integer.valueOf(matchDelta.group("pool")),
                Integer.valueOf(matchDelta.group("thread"))
        );
        logObjects.put(logObject.getObjectID(), logObject);
    }

    private static void ProcessOpening(Matcher matcher) {
        LOGGER.fine("Starting OPENING Processing");
        LOGGER.fine(LocalDateTime.parse(matcher.group("dateTime"), dateFormatter).toString());
        LOGGER.fine(matcher.group("objType"));
        LOGGER.fine(matcher.group("objID"));
        LOGGER.fine(matcher.group("objPath"));
        LOGGER.fine(matcher.group("pathType"));
        QueuedItem queuedItem = logObjects.get(Integer.valueOf(matcher.group("objID")));
        if (queuedItem != null) {
            queuedItem.setOpenTime(LocalDateTime.parse(matcher.group("dateTime"), dateFormatter));
            queuedItem.setObjectType(matcher.group("objType"));
            queuedItem.setObjectPath(matcher.group("objPath"));
            queuedItem.setFolderType(matcher.group("pathType"));
        } else {
            QueuedItem logObject = new QueuedItem(
                    Integer.valueOf(matcher.group("objID")),
                    matcher.group("objType"),
                    matcher.group("objPath"),
                    matcher.group("pathType"),
                    "",
                    LocalDateTime.MIN,
                    LocalDateTime.parse(matcher.group("dateTime"), dateFormatter),
                    LocalDateTime.MIN,
                    LocalDateTime.MIN,
                    -1,
                    -1,
                    logObjects.size() + 1,
                    Integer.valueOf(matcher.group("pool")),
                    Integer.valueOf(matcher.group("thread"))
            );
            logObjects.put(logObject.getObjectID(), logObject);
        }
    }

    private static void ProcessClosing(Matcher matcher) {
        LOGGER.fine("Starting CLOSING Processing");
        LOGGER.fine(LocalDateTime.parse(matcher.group("dateTime"), dateFormatter).toString());
        LOGGER.fine(matcher.group("objType"));
        LOGGER.fine(matcher.group("objID"));
        LOGGER.fine(matcher.group("extrTime"));
        QueuedItem queuedItem = logObjects.get(Integer.valueOf(matcher.group("objID")));
        if (queuedItem != null) {
            queuedItem.setCloseTime(LocalDateTime.parse(matcher.group("dateTime"), dateFormatter));
            queuedItem.setExtractionDuration(Integer.valueOf(matcher.group("extrTime")));
        } else {
            queuedItem = new QueuedItem(Integer.valueOf(matcher.group("objID")),
                    matcher.group("objType"),
                    "",
                    "",
                    "",
                    LocalDateTime.MIN,
                    LocalDateTime.MIN,
                    LocalDateTime.parse(matcher.group("dateTime"), dateFormatter),
                    LocalDateTime.MIN,
                    Integer.valueOf(matcher.group("extrTime")),
                    -1,
                    logObjects.size() + 1,
                    Integer.valueOf(matcher.group("pool")),
                    Integer.valueOf(matcher.group("thread"))
            );
            LOGGER.log(Level.WARNING, "No Match found for Closing Object_ID {0}", matcher.group("objID"));
        }
        logObjects.put(Integer.valueOf(matcher.group("objID")), queuedItem);
    }

    private static void ProcessInserting(Matcher matcher) {
        LOGGER.fine("Starting INSERTING Processing");
        LOGGER.fine(LocalDateTime.parse(matcher.group("dateTime"), dateFormatter).toString());
        LOGGER.fine(matcher.group("objType"));
        LOGGER.fine(matcher.group("objID"));
        LOGGER.fine(matcher.group("insertTime"));
        QueuedItem logObject = logObjects.get(Integer.valueOf(matcher.group("objID")));
        if (logObject != null) {
            logObject.setInsertTime(LocalDateTime.parse(matcher.group("dateTime"), dateFormatter));
            logObject.setInsertionDuration(Integer.valueOf(matcher.group("insertTime")));
        } else {
            logObject = new QueuedItem(Integer.valueOf(matcher.group("objID")),
                    matcher.group("objType"),
                    "",
                    "",
                    "",
                    LocalDateTime.MIN,
                    LocalDateTime.MIN,
                    LocalDateTime.MIN,
                    LocalDateTime.parse(matcher.group("dateTime"), dateFormatter),
                    -1,
                    Integer.valueOf(matcher.group("insertTime")),
                    logObjects.size() + 1,
                    Integer.valueOf(matcher.group("pool")),
                    Integer.valueOf(matcher.group("thread"))
            );
            LOGGER.log(Level.WARNING, "No Match found for Inserting Object_ID {0}", matcher.group("objID"));
        }
        logObjects.put(Integer.valueOf(matcher.group("objID")), logObject);
        InsertRowMain(Integer.valueOf(matcher.group("objID")), true);
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
        cell = row.createCell(cellIndex++, CellType.STRING);
        cell.setCellValue(logObject.getDeltaCause());
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getDeltaTime());
        cell.setCellStyle(dateStyle);
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getOpenTime());
        cell.setCellStyle(dateStyle);
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getCloseTime());
        cell.setCellStyle(dateStyle);
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getInsertTime());
        cell.setCellStyle(dateStyle);
        if (logObject.getDeltaCause().isEmpty() || logObject.openTime.equals(LocalDateTime.MIN)) {
            row.createCell(cellIndex++, CellType.BLANK);
        } else {
            cell = row.createCell(cellIndex++, CellType.NUMERIC);
            cell.setCellValue(logObject.getDeltaTime().until(logObject.getOpenTime(), ChronoUnit.SECONDS));
            cell.setCellStyle(integerStyle);
        }
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellValue(logObject.getExtractionDuration());
        cell.setCellStyle(integerStyle);
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellStyle(integerStyle);
        cell.setCellValue(logObject.getInsertionDuration());
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellStyle(integerStyle);
        cell.setCellValue(logObject.getNumberInQueue());
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellStyle(integerStyle);
        cell.setCellValue(logObject.getPool());
        cell = row.createCell(cellIndex++, CellType.NUMERIC);
        cell.setCellStyle(integerStyle);
        cell.setCellValue(logObject.getThread());
        if (delEntry) {
            logObjects.remove(objID);
        }
    }

    private static boolean CheckOnePattern(PatternEntry patternEntry, String inputLine) {
        Matcher matcher = patternEntry.getPattern().matcher(inputLine);
        if (!matcher.find()) {
            return false;
        }
        LOGGER.log(Level.FINE, "Matched pattern {0}", patternEntry.getPatternStr());

        XSSFSheet sheet;
        if (patternEntry.isIsFound()) {
            sheet = workbook.getSheet(patternEntry.getSheetName());
        } else {
            patternEntry.setIsFound(true);
            sheet = workbook.createSheet(patternEntry.getSheetName());
            LOGGER.log(Level.INFO, "Creating new sheet {0}", patternEntry.getSheetName());
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
                case "D":
                    cell.setCellValue(LocalDateTime.parse(value, dateFormatter));
                    break;
                case "N":
                    cell.setCellValue(Double.parseDouble(value));
                    break;
                case "I":
                    cell.setCellValue(Integer.parseInt(value));
                    break;
                default:
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
        private String objectType;
        private String objectPath;
        private String folderType;
        private final String deltaCause;
        private final LocalDateTime deltaTime;
        private LocalDateTime openTime;
        private LocalDateTime closeTime;
        private LocalDateTime insertTime;
        private Integer extractionDuration;
        private Integer insertionDuration;
        private final Integer numberInQueue;
        private final Integer pool;
        private final Integer thread;

        public QueuedItem(
                Integer objectID,
                String objectType,
                String objectPath,
                String folderType,
                String deltaCause,
                LocalDateTime deltaTime,
                LocalDateTime openTime,
                LocalDateTime closeTime,
                LocalDateTime insertTime,
                Integer extractionDuration,
                Integer insertionDuration,
                Integer numberInQueue,
                Integer pool,
                Integer thread) {
            this.objectID = objectID;
            this.objectType = objectType;
            this.objectPath = objectPath;
            this.folderType = folderType;
            this.deltaCause = deltaCause;
            this.deltaTime = deltaTime;
            this.openTime = openTime;
            this.closeTime = closeTime;
            this.insertTime = insertTime;
            this.extractionDuration = extractionDuration;
            this.insertionDuration = insertionDuration;
            this.numberInQueue = numberInQueue;
            this.pool = pool;
            this.thread = thread;
        }

        public void setObjectType(String objectType) {
            this.objectType = objectType;
        }

        public void setObjectPath(String objectPath) {
            this.objectPath = objectPath;
        }

        public void setFolderType(String folderType) {
            this.folderType = folderType;
        }

        public void setOpenTime(LocalDateTime openTime) {
            this.openTime = openTime;
        }

        public void setCloseTime(LocalDateTime closeTime) {
            this.closeTime = closeTime;
        }

        public void setInsertTime(LocalDateTime insertTime) {
            this.insertTime = insertTime;
        }

        public void setExtractionDuration(Integer extractionDuration) {
            this.extractionDuration = extractionDuration;
        }

        public void setInsertionDuration(Integer insertionDuration) {
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

        public String getDeltaCause() {
            return deltaCause;
        }

        public LocalDateTime getDeltaTime() {
            return deltaTime;
        }

        public LocalDateTime getOpenTime() {
            return openTime;
        }

        public LocalDateTime getCloseTime() {
            return closeTime;
        }

        public LocalDateTime getInsertTime() {
            return insertTime;
        }

        public Integer getExtractionDuration() {
            return extractionDuration;
        }

        public Integer getInsertionDuration() {
            return insertionDuration;
        }

        public Integer getNumberInQueue() {
            return numberInQueue;
        }

        public Integer getPool() {
            return pool;
        }

        public Integer getThread() {
            return thread;
        }
    }

    private static class PatternEntry {

        private final String sheetName;
        private final String patternStr;
        private final Pattern pattern;
        private final List< Field> fields;
        private boolean isFound;

        public PatternEntry(
                String sheetName,
                String patternStr,
                Pattern pattern,
                List< Field> fields) {
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

        public Field(
                String name,
                CellType type,
                CellStyle style,
                String dataType) {
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
