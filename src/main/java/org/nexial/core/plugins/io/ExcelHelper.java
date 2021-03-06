/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.db.DaoUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.lang.System.lineSeparator;
import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Project.SCRIPT_FILE_EXT;
import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;

public class ExcelHelper {
    private static final DateFormat DEF_EXCEL_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
    private ExecutionContext context;

    public ExcelHelper(ExecutionContext context) { this.context = context; }

    public StepResult saveCsvToFile(File excelFile, String worksheet, String csvFile) {
        String excel = excelFile.getAbsolutePath();
        boolean newerFormat = StringUtils.endsWith(excel, SCRIPT_FILE_EXT);
        try {
            StringBuilder csv = newerFormat ? xlsx2csv(excelFile, worksheet) : xls2csv(excelFile, worksheet);
            return saveCSVContentToFile(csvFile, csv);
        } catch (IOException e) {
            return StepResult.fail("Unable to read excel file '" + excel + "': " + e.getMessage());
        }
    }

    public static void csv2xlsx(String file, String sheet, String startCell, List<List<String>> rowsAndColumns)
        throws IOException {
        // either write access or write down would work.
        if (StringUtils.isBlank(startCell)) { startCell = "A1"; }

        File f = new File(file);
        Excel excel = FileUtil.isFileReadable(file, MIN_EXCEL_FILE_SIZE) ? new Excel(f) : Excel.newExcel(f);
        Worksheet worksheet = excel.worksheet(sheet, true);
        worksheet.writeAcross(new ExcelAddress(startCell), rowsAndColumns);
    }

    protected StringBuilder xlsx2csv(File excelFile, String worksheet) throws IOException {
        XSSFWorkbook workBook = new XSSFWorkbook(new FileInputStream(excelFile));
        XSSFSheet excelSheet = workBook.getSheet(worksheet);
        Iterator rowIterator = excelSheet.rowIterator();
        StringBuilder csv = new StringBuilder();
        String delim = context.getTextDelim();

        while (rowIterator.hasNext()) {
            XSSFRow row = (XSSFRow) rowIterator.next();

            String oneRow = "";
            String value;
            for (int i = 0; i < row.getLastCellNum(); i++) {
                value = returnCellValue(row.getCell(i));
                if (StringUtils.isEmpty(value)) { value = ""; }
                oneRow += value + delim;
            }

            oneRow = StringUtils.trim(StringUtils.removeEnd(oneRow, delim));
            if (!oneRow.isEmpty()) { csv.append(oneRow).append(lineSeparator()); }
        }

        return csv;
    }

    protected StringBuilder xls2csv(File excelFile, String worksheet) throws IOException {
        HSSFWorkbook workBook = new HSSFWorkbook(new POIFSFileSystem(new FileInputStream(excelFile)));
        HSSFSheet excelSheet = workBook.getSheet(worksheet);
        Iterator rowIterator = excelSheet.rowIterator();
        StringBuilder csv = new StringBuilder();
        String delim = context.getTextDelim();

        while (rowIterator.hasNext()) {
            HSSFRow row = (HSSFRow) rowIterator.next();

            String oneRow = "";
            String value;
            for (int i = 0; i < row.getLastCellNum(); i++) {
                HSSFCell cell = row.getCell(i);
                value = returnCellValue(cell);
                if (StringUtils.isEmpty(value)) { value = ""; }
                oneRow += value + delim;
            }

            oneRow = StringUtils.trim(StringUtils.removeEnd(oneRow, delim));
            if (!oneRow.isEmpty()) { csv.append(oneRow).append(lineSeparator()); }
        }

        return csv;
    }

    protected StepResult saveCSVContentToFile(String file, StringBuilder csv) {
        String content = csv.toString();
        if (context.isVerbose()) {
            context.getLogger().log(context,
                                    "writing " + StringUtils.countMatches(content, "\n") + " row(s) to '" + file + "'");
        }

        File target = new File(file);
        try {
            FileUtils.forceMkdir(target.getParentFile());
            FileUtils.write(target, content, DEF_CHARSET);
            return StepResult.success("File converted to CSV");
        } catch (IOException e) {
            String error = "Error writing CSV content to '" + file + "': " + e.getMessage();
            ConsoleUtils.log(error);
            FileUtils.deleteQuietly(target);
            return StepResult.fail(error);
        }
    }

    protected String returnCellValue(HSSFCell cell) { return returnCellValue(((Cell) cell)); }

    protected String returnCellValue(XSSFCell cell) { return returnCellValue(((Cell) cell)); }

    protected String returnCellValue(Cell cell) {
        try {
            String value;

            switch (cell.getCellTypeEnum()) {
                case STRING:
                case BOOLEAN:
                    value = cell.getRichStringCellValue().toString();
                    break;
                case NUMERIC:
                    value = formattedCellToString(cell);
                    break;
                case FORMULA:
                    CellType resultType = cell.getCachedFormulaResultTypeEnum();
                    if (resultType == STRING) {
                        value = cell.getRichStringCellValue().toString();
                    } else if (resultType == NUMERIC) {
                        value = formattedCellToString(cell);
                    } else {
                        value = cell.getStringCellValue();
                    }

                    break;
                default:
                    value = cell.getStringCellValue();
            }

            return DaoUtils.csvFriendly(value, context.getTextDelim(), true);
        } catch (Exception e) {
            return null;
        }
    }

    protected String formattedCellToString(Cell cell) {
        if (HSSFDateUtil.isCellDateFormatted(cell)) {
            return DEF_EXCEL_DATE_FORMAT.format(cell.getDateCellValue());
        } else {
            return String.valueOf(cell.getNumericCellValue());
        }
    }
}
