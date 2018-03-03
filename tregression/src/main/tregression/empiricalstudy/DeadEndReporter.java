package tregression.empiricalstudy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import tregression.empiricalstudy.training.ControlDeadEndData;
import tregression.empiricalstudy.training.DataDeadEndData;
import tregression.empiricalstudy.training.DeadEndData;

public class DeadEndReporter {
	private String fileTitle = "dead-end";
	
	private File file;
	
	private Sheet localDataSheet;
	private Sheet fieldSheet;
	private Sheet arraySheet;
	private Sheet controlSheet;
	
	public static String LOCAL_DATA_SHEET = "local_var";
	public static String FIELD_SHEET = "field";
	public static String ARRAY_SHEET = "array";
	public static String CONTROL_SHEET = "control";
	
	private Workbook book;
	private int lastLocalVarRowNum = 1;
	private int lastFieldRowNum = 1;
	private int lastArrayRowNum = 1;
	private int lastControlRowNum = 1;
	
	private int filePage = 0;
	private int trialNumberLimitPerFile = 1000000;
	
	public DeadEndReporter() throws IOException{
		String fileName = fileTitle + filePage + ".xlsx";
		file = new File(fileName);
		
		while(file.exists()){
			InputStream excelFileToRead = new FileInputStream(file);
			book = new XSSFWorkbook(excelFileToRead);
			localDataSheet = book.getSheet(LOCAL_DATA_SHEET);
			fieldSheet = book.getSheet(FIELD_SHEET);
			arraySheet = book.getSheet(ARRAY_SHEET);
			controlSheet = book.getSheet(CONTROL_SHEET);
			
			lastLocalVarRowNum = localDataSheet.getPhysicalNumberOfRows();
			lastFieldRowNum = fieldSheet.getPhysicalNumberOfRows();
			lastArrayRowNum = arraySheet.getPhysicalNumberOfRows();
			lastControlRowNum = controlSheet.getPhysicalNumberOfRows();
//			if(lastRowNum > trialNumberLimitPerFile){
//				filePage++;
//				fileName = fileTitle + filePage + ".xlsx";
//				file = new File(fileName);
//			}
//			else{
//				break;
//			}
			break;
		}
		
		if(!file.exists()){
			initializeNewExcel();
		}
	}
	
	private void initializeNewExcel() {
		book = new XSSFWorkbook();
		createLocalVarSheet();
		createFieldSheet();
		createArraySheet();
		createControlSheet();
	}
	
	private void createControlSheet() {
		controlSheet = book.createSheet("control");
		
		List<String> titles = new ArrayList<>();
		titles.add("project");
		titles.add("bug_ID");
		titles.add("test_case");
		titles.add("trace_order");
		titles.add("is_break_step");
		
		titles.add("move_ups");
		titles.add("move_downs");
		titles.add("move_rights");
		
		titles.add("data_dependency");
		titles.add("control_dependency");
		
		Row row = controlSheet.createRow(0);
		for(int i = 0; i < titles.size(); i++){
			row.createCell(i).setCellValue(titles.get(i)); 
		}
		
		this.lastControlRowNum = 1;
	}

	private void createLocalVarSheet() {
		localDataSheet = book.createSheet(LOCAL_DATA_SHEET);
		
		List<String> titles = new ArrayList<>();
		titles.add("project");
		titles.add("bug_ID");
		titles.add("test_case");
		titles.add("trace_order");
		titles.add("is_break_step");
		
		titles.add("critical_conditional_step");
		titles.add("w_local_var_type");
		titles.add("w_local_var_name");
		
		titles.add("r_local_var_type");
		titles.add("r_local_var_name");
		
		Row row = localDataSheet.createRow(0);
		for(int i = 0; i < titles.size(); i++){
			row.createCell(i).setCellValue(titles.get(i)); 
		}
		
		this.lastLocalVarRowNum = 1;
	}
	
	private void createFieldSheet() {
		fieldSheet = book.createSheet(FIELD_SHEET);
		
		List<String> titles = new ArrayList<>();
		titles.add("project");
		titles.add("bug_ID");
		titles.add("test_case");
		titles.add("trace_order");
		titles.add("is_break_step");
		
		titles.add("critical_conditional_step");
		titles.add("w_parent");
		titles.add("w_parent_type");
		titles.add("w_type");
		titles.add("w_name");
		
		titles.add("r_parent");
		titles.add("r_parent_type");
		titles.add("r_type");
		titles.add("r_name");
		
		Row row = fieldSheet.createRow(0);
		for(int i = 0; i < titles.size(); i++){
			row.createCell(i).setCellValue(titles.get(i)); 
		}
		
		this.lastFieldRowNum = 1;
	}
	
	private void createArraySheet() {
		arraySheet = book.createSheet(ARRAY_SHEET);
		
		List<String> titles = new ArrayList<>();
		titles.add("project");
		titles.add("bug_ID");
		titles.add("test_case");
		titles.add("trace_order");
		titles.add("is_break_step");
		
		titles.add("critical_conditional_step");
		titles.add("w_parent");
		titles.add("w_type");
		titles.add("w_name");
		
		titles.add("r_parent");
		titles.add("r_type");
		titles.add("r_name");
		
		Row row = arraySheet.createRow(0);
		for(int i = 0; i < titles.size(); i++){
			row.createCell(i).setCellValue(titles.get(i)); 
		}
		
		this.lastArrayRowNum = 1;
	}

	public void export(List<DeadEndData> dataList, String project, int bugID) {
		
		if(!dataList.isEmpty()) {
			for(DeadEndData data: dataList) {
				if(data instanceof DataDeadEndData){
					
					DataDeadEndData ddd = (DataDeadEndData)data;
					if(ddd.type==DataDeadEndData.LOCAL_VAR){
						Row row = this.localDataSheet.createRow(this.lastLocalVarRowNum);
						fillLocalVarRowInformation(row, ddd, project, bugID);
						this.lastLocalVarRowNum++;
					}
					else if(ddd.type==DataDeadEndData.FIELD){
						Row row = this.fieldSheet.createRow(this.lastFieldRowNum);
						fillFieldRowInformation(row, ddd, project, bugID);
						this.lastFieldRowNum++;
					}
					else if(ddd.type==DataDeadEndData.ARRAY_ELEMENT){
						Row row = this.arraySheet.createRow(this.lastArrayRowNum);
						fillArrayRowInformation(row, ddd, project, bugID);
						this.lastArrayRowNum++;
					}
					
				}
				else if(data instanceof ControlDeadEndData){
					Row row = this.controlSheet.createRow(this.lastControlRowNum);
					fillControlRowInformation(row, (ControlDeadEndData)data, project, bugID);
					this.lastControlRowNum++;
				}
				
			}
		}
		
		writeToExcel(book, file.getName());
		
//		if(lastRowNum > trialNumberLimitPerFile){
//			filePage++;
//			String fileName = fileTitle + filePage + ".xlsx";
//			file = new File(fileName);
//			
//			initializeNewExcel();
//		}
	}
	
	private void fillLocalVarRowInformation(Row row, DataDeadEndData data, String project, int bugID) {
		
		row.createCell(0).setCellValue(project);
		row.createCell(1).setCellValue(bugID);
		row.createCell(2).setCellValue(data.testcase);
		row.createCell(3).setCellValue(data.traceOrder);
		row.createCell(4).setCellValue(data.isBreakStep);
		
		row.createCell(5).setCellValue(data.criticalConditionalStep);
		
		row.createCell(6).setCellValue(data.sameWLocalVarType);
		row.createCell(7).setCellValue(data.sameWLocalVarName);
		
		
		row.createCell(8).setCellValue(data.sameRLocalVarType);
		row.createCell(9).setCellValue(data.sameRLocalVarName);
		
	}
	
	private void fillFieldRowInformation(Row row, DataDeadEndData data, String project, int bugID) {
		
		row.createCell(0).setCellValue(project);
		row.createCell(1).setCellValue(bugID);
		row.createCell(2).setCellValue(data.testcase);
		row.createCell(3).setCellValue(data.traceOrder);
		row.createCell(4).setCellValue(data.isBreakStep);
		
		row.createCell(5).setCellValue(data.criticalConditionalStep);
		
		row.createCell(6).setCellValue(data.sameWFieldParent);
		row.createCell(7).setCellValue(data.sameWFieldParentType);
		row.createCell(8).setCellValue(data.sameWFieldType);
		row.createCell(9).setCellValue(data.sameWFieldName);		
		
		row.createCell(10).setCellValue(data.sameRFieldParent);
		row.createCell(11).setCellValue(data.sameRFieldParentType);
		row.createCell(12).setCellValue(data.sameRFieldType);
		row.createCell(13).setCellValue(data.sameRFieldName);
		
	}
	
	private void fillArrayRowInformation(Row row, DataDeadEndData data, String project, int bugID) {
		
		row.createCell(0).setCellValue(project);
		row.createCell(1).setCellValue(bugID);
		row.createCell(2).setCellValue(data.testcase);
		row.createCell(3).setCellValue(data.traceOrder);
		row.createCell(4).setCellValue(data.isBreakStep);
		
		row.createCell(5).setCellValue(data.criticalConditionalStep);
		
		row.createCell(6).setCellValue(data.sameWArrayParent);
		row.createCell(7).setCellValue(data.sameWArrayType);
		row.createCell(8).setCellValue(data.sameRArrayIndex);		
		
		row.createCell(9).setCellValue(data.sameRArrayParent);
		row.createCell(10).setCellValue(data.sameRArrayType);
		row.createCell(11).setCellValue(data.sameRArrayIndex);
		
	}
	
	private void fillControlRowInformation(Row row, ControlDeadEndData data, String project, int bugID) {
		
		row.createCell(0).setCellValue(project);
		row.createCell(1).setCellValue(bugID);
		row.createCell(2).setCellValue(data.testcase);
		row.createCell(3).setCellValue(data.traceOrder);
		row.createCell(4).setCellValue(data.isBreakStep);
		
		row.createCell(5).setCellValue(data.moveUps);
		row.createCell(6).setCellValue(data.moveDowns);
		row.createCell(7).setCellValue(data.moveRights);
		
		row.createCell(8).setCellValue(data.dataDependency);
		row.createCell(9).setCellValue(data.controlDependency);
	}
	
	private void writeToExcel(Workbook book, String fileName){
		try {
			FileOutputStream fileOut = new FileOutputStream(fileName);
			book.write(fileOut); 
			fileOut.close(); 
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
}
