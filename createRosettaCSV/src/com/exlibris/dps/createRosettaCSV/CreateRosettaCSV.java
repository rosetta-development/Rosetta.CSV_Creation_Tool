package com.exlibris.dps.createRosettaCSV;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.log4j.Logger;
import org.apache.commons.io.comparator.NameFileComparator;


public class CreateRosettaCSV 
{
	private static enum SECTIONS { source, general, target };
	private static final Logger logger = Logger.getLogger(CreateRosettaCSV.class);
	static private XMLProperty properties;
	static private String sourcerootcsv;
	static private String sourcerootfiles;
	static private String csvname;
	static private String labelregex;
	static private String targetdircsv;
	static private String currentDir;
	static boolean debug = false;
	static boolean windows = false;
	static private LogWindow logwindow;
	static private String nfspathtostreams;
	static boolean zipstreamfolder = false;
	static boolean csvexists = false;
	static private String separator = "/";


	public CreateRosettaCSV() throws Exception
	{
		logger.info("OS name=" + System.getProperty("os.name"));

		get_properties();
		debug = Boolean.parseBoolean(properties.getProperty(SECTIONS.general.toString(), "debug"));
	}

	@SuppressWarnings({ "deprecation", "static-access" })
	protected void finalize() throws Exception
	{
		logger.info("END");
		logger.shutdown();
	}

	/*
	 * Unique exception formatting
	 */
	private void format_exception(Exception e)
	{
		StackTraceElement[] st = e.getStackTrace();
		for (StackTraceElement se : st)
		{
			if(se.getClassName().contains(this.getClass().getSimpleName()))
			{
				logger.error(e.getClass().getSimpleName() + " = \"" + e.getMessage() + "\" (triggered by " + se.getClassName() + ":" + se.getLineNumber() + ")");
				break;
			}
		}
	}

	/*
	 * Read properties from external file
	 */
	private	void get_properties() throws Exception
	{
		properties = new XMLProperty();
		String pn = "conf/" + this.getClass().getSimpleName() + ".xml";
		properties.load(pn);
		List<String> lines = Files.readAllLines(Paths.get(pn));
		currentDir = System.getProperty("user.dir");
		logger.info("Following parameters have been set in ");
		logger.info("==> " + currentDir + "/" + pn + ":");
		logger.info("------------------------------------------------------");
		for(String line : lines) {
			if (line.replaceAll(" ", "").startsWith("<") && !line.replaceAll(" ", "").startsWith("<!")) {
				logger.info(line);
			}
		}
		logger.info("------------------------------------------------------");
	}

	/*
	 * Check arguments
	 * Read parameters from configuration file
	 */
	private	void get_sourcecsv() throws Exception
	{

		sourcerootcsv = properties.getProperty(SECTIONS.source.toString(), "sourcerootcsv");
		if (sourcerootcsv==null || (sourcerootcsv!=null && sourcerootcsv.isEmpty())) {
			if (windows) {
				logwindow.showInfo("Path to CSV file is not configured - exited" + System.lineSeparator());
			}
			throw new Exception("Path to CSV file is not configured - exited");
		} else if (!sourcerootcsv.endsWith("/") && !sourcerootcsv.endsWith("\\")) {
			sourcerootcsv = sourcerootcsv + separator;
		}

		File csvdir = new File(sourcerootcsv);
		if (!csvdir.isDirectory()) {
			if (windows) {
				logwindow.showInfo("Directory \"" + csvdir.getAbsolutePath() + "\" not found - exited" + System.lineSeparator());
			}
			throw new Exception("Directory \"" + csvdir.getAbsolutePath() + "\" not found - exited");
		}

		sourcerootfiles = properties.getProperty(SECTIONS.source.toString(), "sourcerootfiles");
		if (sourcerootfiles==null || (sourcerootfiles!=null && sourcerootfiles.isEmpty())) {
			if (windows) {
				logwindow.showInfo("Path to files is not configured - exited" + System.lineSeparator());
			}
			throw new Exception("Path to files is not configured - exited");
		} else if (!sourcerootfiles.endsWith("/") && !sourcerootfiles.endsWith("\\")) {
			sourcerootfiles = sourcerootfiles + separator;
		}
		
		labelregex = properties.getProperty(SECTIONS.source.toString(), "labelregex");

		File filedir = new File(sourcerootfiles);
		if (!filedir.isDirectory()) {
			if (windows) {
				logwindow.showInfo("Directory \"" + filedir.getAbsolutePath() + "\" not found - exited" + System.lineSeparator());
			}
			throw new Exception("Directory \"" + filedir.getAbsolutePath() + "\" not found - exited");
		}
	}

	/*
	 *  Check and create target root directory if necessary
	 */
	private void create_targetroot() throws Exception
	{
		// Create target directory
		targetdircsv = properties.getProperty(SECTIONS.target.toString(), "targetrootcsv");
		if (targetdircsv==null || (targetdircsv!=null && targetdircsv.isEmpty())) {
			if (windows) {
				logwindow.showInfo("Target folder is not configured - exited" + System.lineSeparator());
			}
			throw new Exception("Target directory is not configured - exited");
		} else if (!targetdircsv.endsWith("/") && !targetdircsv.endsWith("\\")) {
			targetdircsv = targetdircsv + separator;
		}

		File csvroot = new File(targetdircsv);
		if (!csvroot.exists())
		{
			logger.info("Creating directory " + targetdircsv);
			if(!csvroot.mkdirs())
				throw new Exception("Unable to create directory \"" + csvroot.getAbsolutePath() + "\"");
		}
	}

	/*
	 * Start creating the CSV
	 * read folder that contains original (simple) CSV file
	 */
	private void read_csv_files() throws Exception
	{
		File files_dir = new File(sourcerootcsv);

		/*
		 * read CSV file line by line into basenameIEmap (hash map with two strings: base name + complete line per IE)
		 * IMPORTANT: first column must have header 'Filename for matching'
		 * first line contains header
		 */
		File[] files = files_dir.listFiles();

		for( File file : files ) {
			if (file.getName().toLowerCase().matches(".*\\.csv")) {
				csvname = file.getName();
				csvexists = true;
				LinkedHashMap<String,String> basenameIEmap = new LinkedHashMap<String,String>();

				if (windows) {
					logwindow.showInfo("START processing" + System.lineSeparator());
					logwindow.showInfo("Reading original CSV '" + sourcerootcsv + csvname + "'.");
					logwindow.showInfo(System.lineSeparator());
				}
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine()) != null) {
					String basename = line.substring(0, line.indexOf(","));
					if (basename.isEmpty()) {
						basename = "SIP";
					}
					String ie_csv_line= line.substring(line.indexOf(",")+1);
					basenameIEmap.put(basename, ie_csv_line);
				}
				reader.close();

				String headerline = basenameIEmap.get("Filename for matching");
				String sipline = basenameIEmap.get("SIP");
				if (headerline != null && headerline.contains(",")) {
					basenameIEmap.remove("Filename for matching");
					basenameIEmap.remove("SIP");
					create_csv(csvname, basenameIEmap, headerline, sipline);
				} else {
					throw new Exception("CSV file is not valid. Please check!");
				}
			} else if (!csvexists) {
				if (windows) {
					logwindow.showInfo("There is no CSV file in " + sourcerootcsv + ". Please check!");
				}
				throw new Exception("There is no CSV file in " + sourcerootcsv + ". Please check!");
			}
		}
	}

	/*
	 * Parse file stream sub-directories, one subdir per Preservation Type of the representation
	 * * folder names - which are preservation types at the same time - are configured in <repfolder> parameter
	 * find all files starting with one of the base names
	 * return list of relevant file names
	 */
	private List<String> get_stream_names(String basename, String subdir) throws Exception
	{
		List<String> filenames = new ArrayList<String>();
		String[] basenames;

		subdir = sourcerootfiles+subdir;

		File files_dir = new File(subdir);
		if (!files_dir.isDirectory()) {
			if (windows) {
				logwindow.showInfo("ERROR: Files directory \"" + files_dir.getAbsolutePath() + "\" not found." + System.lineSeparator());
				logwindow.showInfo("Check <sourcerootfiles> parameter." + System.lineSeparator());
			}
			throw new Exception ("Files directory \"" + files_dir.getAbsolutePath() + "\" not found. Check <sourcerootfiles> parameter.");
		}


		File[] files = files_dir.listFiles();
		Arrays.sort(files, NameFileComparator.NAME_COMPARATOR);

		if (basename.contains("|")) {
			basenames = basename.split("\\|");
		} else {
			basenames = new String[1];
			basenames[0] = basename;
		}

		for ( String base : basenames) {
			for( File file : files ) {
				String filenamefull = file.getCanonicalPath();
				String filename = file.getName();
				if(filename.startsWith(base)) {
					filenames.add(filenamefull);
					if (debug) logger.debug("base " + base + " adds file " + filenamefull);
				}
			}
		}

		return filenames;
	}	

	/*
	 * Create and write CSV
	 */
	private void create_csv(String csvname, LinkedHashMap<String,String> basenameIEmap, String oldheaderline, String oldsipline) throws Exception
	{
		/*
		 * IMPORTANT
		 * requirements for the provided CSV:
		 *   separator is comma (,)
		 *   first column must have header 'Filename for matching'
		 *   first line contains header
		 *   header values must not contain comma
		 *   there can be only one line for object type SIP
		 *   there is exact one line per IE
		 *   there are no other lines than for object type SIP or IE
		 *   all columns that should be filled by the tool MUST NOT already exist in the original CSV, e.g.
		 *     Preservation Type, File Original Path, File Original Name, File Label etc.
		 * 'Preservation Type' will be the SAME as the folder name that contains the streams of this representation
		 *    and are configured in <repfolder> (e.g. <repfolder>PRESERVATION_MASTER,MODIFIED_MASTER,DERIVATIVE_COPY</repfolder>)
		 * 'File Original Path' will be {representation folder} + '/', e.g. 'PRESERVATION_MASTER/' --> the files will be zipped together with their folder
		 * 'File Original Name' will be the file name
		 * 'File Label' will be taken from file name via regex
		 * 
		 * 'FILE - Title (DC)' and 'FILE - Description (DC)' could be filled with file name as well but there is no need (from my point of view)
		 */
		/*
		 * concept is to add all representation and file related lines and columns
		 * first column containing 'Filename for matching' has been removed when building the hash map (basenameIEmap)
		 * number of existing columns must be calculated from 'headerline' to generate valid new lines
		 * new CSV lines are added to string array 'NewCSV'
		 * 'NewCSV' is written to file 'csvname' in 'targetdircsv'
		 */

		ArrayList<String> NewCSV = new ArrayList<String>();
		String newheaderline = new String();
		String newsipline = new String();
		String iesipcommas = new String();
		String repcommasmiddle = new String();
		String repcommasafter = new String();
		String repline = new String();
		String fileline = new String();
		boolean addfullpath = false;

		String newline = System.getProperty("line.separator"); // this will take proper line break depending on OS
		String[] reps = properties.getProperty(SECTIONS.source.toString(), "repfolder").split(","); // get representation folders
		if (!windows) {
			addfullpath = Boolean.parseBoolean(properties.getProperty(SECTIONS.general.toString(), "addfullpath"));
		}
		if (windows) {
			zipstreamfolder = Boolean.parseBoolean(properties.getProperty(SECTIONS.general.toString(), "zipstreamfolder"));
			nfspathtostreams = properties.getProperty(SECTIONS.target.toString(), "nfspathtostreams");
			if (zipstreamfolder && !nfspathtostreams.isEmpty()) {
				logwindow.showInfo("!!! Potential configuration mistake: Parameter <nfspathtostreams> will be ignored because <zipstreamfolder> is 'true'." + System.lineSeparator());
				logwindow.showInfo("!!! Please check and adapt the configuration in case this was not your intention - or ignore this message, otherwise." + System.lineSeparator());
				logwindow.showInfo(System.lineSeparator());
				nfspathtostreams = "";
			}
		}
		int oldnumcolumns = 0;
		int newnumcolumns = 0;

		oldnumcolumns = oldheaderline.length()-oldheaderline.replace(",", "").length()+1; //get number of columns for old CSV

		newheaderline = oldheaderline + "," + "Preservation Type" + "," + "File Original Path" + "," + "File Original Name" + "," + "File Label";

		newnumcolumns = newheaderline.length()-newheaderline.replace(",", "").length()+1; //get number of columns for new CSV

		/*
		 * calculate commas for REP line
		 * calculate commas to be added to the end of IE lines and to the SIP line
		 * create new SIP line
		 */
		for (int i = 0; i < oldnumcolumns; i++) {
			repcommasmiddle = repcommasmiddle + ",";
		}
		for (int i = 0; i < newnumcolumns-oldnumcolumns-1; i++) {
			repcommasafter = repcommasafter + ",";
		}
		for (int i = 0; i < newnumcolumns-oldnumcolumns; i++) {
			iesipcommas = iesipcommas + ",";
		}

		newsipline = oldsipline + iesipcommas;

		/*
		 * add new header and SIP line
		 */
		NewCSV.add(newheaderline);
		NewCSV.add(newsipline);

		/*
		 * create all other lines for new CSV
		 */
		for (Map.Entry<String, String> entry : basenameIEmap.entrySet()) {
			String basename = entry.getKey();
			String ieline = entry.getValue();

			ieline = ieline + iesipcommas;
			NewCSV.add(ieline); // add IE line

			for (String rep : reps) {
				repline = "REPRESENTATION" + repcommasmiddle + rep + repcommasafter;

				List<String> filenames = get_stream_names(basename, rep);
				
				if (!filenames.isEmpty()) {
					NewCSV.add(repline); // add REP line if there are files for this representation
				}
				
				rep = nfspathtostreams + rep; //add absolute path from parameter <nfspathtostreams> as prefix to representation folder name
				for (String filenamefull : filenames) {
					String filename = filenamefull.replaceAll("\\\\", "/").replaceAll("^.*/", "");
					String filepath = filenamefull.replaceAll("\\\\", "/").substring(filenamefull.replaceAll("\\\\", "/").indexOf("/"),filenamefull.replaceAll("\\\\", "/").lastIndexOf("/"));
					if (debug) logger.debug("filenamefull: " + filenamefull);
					if (debug) logger.debug("filename: " + filename);
					if (debug) logger.debug("local filepath: " + filepath);

					String filelabel = filename;
					try {
						if (labelregex != null) {
							Pattern p = Pattern.compile(labelregex);
							Matcher m = p.matcher(filename);
							while (m.matches()) {
								filelabel = m.group(1);
								if (debug) logger.debug("filelabel: " + filelabel);
								break;
							}
						}
					} catch (PatternSyntaxException e) {
						e.printStackTrace();
						if (windows) {
							logwindow.showInfo("Regex pattern syntax exception. Please check <labelregex>" + labelregex + "</labelregex> in CreateRosettaCSV.xml." + System.lineSeparator());
						}
					}


					
					if (addfullpath) {
						fileline = "FILE" + repcommasmiddle + "," + filepath + "/" + "," + filename + "," + filelabel;
					} else {
						fileline = "FILE" + repcommasmiddle + "," + rep + "/" + "," + filename + "," + filelabel;
					}
					NewCSV.add(fileline); // add FILE line
				}
			}
		}

		/*
		 * Unix: create directories for SIP folder
		 * {CSV name}/content
		 */
		if (!windows) {
			File sipdir = new File(targetdircsv + csvname.replaceAll(".csv$", "") + "/content");
			csvname = csvname.replaceAll(".csv$", "") + "/content/" + csvname;

			if (!sipdir.exists())
			{
				logger.info("Creating directory " + sipdir);
				if(!sipdir.mkdirs())
					throw new Exception("Unable to create directory \"" + sipdir.getAbsolutePath() + "\"");
			}
		}

		FileWriter writecsv = new FileWriter(targetdircsv + csvname);
		logger.info("Writing new CSV file " + targetdircsv + csvname);
		if (windows) {
			logwindow.showInfo("Writing new CSV file '" + targetdircsv + csvname + "'." + System.lineSeparator());
		}
		for (int k=0; k<NewCSV.size(); k++)
		{
			if (debug) logger.debug("add line: " + NewCSV.get(k));
			writecsv.write(NewCSV.get(k)+ newline);
		}
		writecsv.close();
		NewCSV.clear();

		/*
		 * create ZIP file for manual upload
		 */
		if (zipstreamfolder && !addfullpath) {
			File files_dir = new File(sourcerootfiles);
			if (debug) logger.debug("sourcerootfiles: " + sourcerootfiles);
			List<File> filestozip = new ArrayList<File>();
			filestozip = get_streams_for_zip(files_dir, filestozip);
			if (!filestozip.isEmpty()) {
				String zipname = csvname.replaceAll("csv$", "") + "zip";
				logger.info("Creating ZIP file '" + zipname + "'");
				if (windows) {
					logwindow.showInfo("Creating ZIP file '" + zipname + "'. Please wait ..." + System.lineSeparator());
				}
				writeZipFile(targetdircsv+zipname, filestozip);
				if (windows) {
					logwindow.showInfo("                                                                ... done.");
					logwindow.showInfo(System.lineSeparator());
				}
			}
		}


	}

	/*
	 * return list of files from all sub-directories below 'files_dir'
	 */
	private static String get_result_folder(String targetdir) throws Exception
	{
		String resultfolder = targetdir;
		if (resultfolder.endsWith("\\")||resultfolder.endsWith("/")) {
			resultfolder = resultfolder.substring(0, (resultfolder.length()-1));
			if (resultfolder.contains("\\")||resultfolder.contains("/")) {
				resultfolder = targetdir;
			} else {
				resultfolder = currentDir + separator + resultfolder; 
			}
		}
		
		return resultfolder;
	}	

	/*
	 * return list of files from all sub-directories below 'files_dir'
	 */
	private List<File> get_streams_for_zip(File files_dir, List<File> filestozip) throws Exception
	{
		if (!files_dir.isDirectory()) {
			if (windows) {
				logwindow.showInfo("ERROR: Files directory \"" + files_dir.getAbsolutePath() + "\" not found." + System.lineSeparator());
				logwindow.showInfo("Check <sourcerootfiles> parameter." + System.lineSeparator());
			}
			throw new Exception ("Files directory \"" + files_dir.getAbsolutePath() + "\" not found. Check <sourcerootfiles> parameter.");
		}


		File[] files = files_dir.listFiles();
		for( File file : files ) {
			filestozip.add(file);
			if (file.isDirectory()) {
				get_streams_for_zip(file, filestozip);
			}
		}
		return filestozip;
	}	

	public static void writeZipFile(String zipname, List<File> filestozip) {

		try {
			FileOutputStream fos = new FileOutputStream(zipname);
			ZipOutputStream zos = new ZipOutputStream(fos);
			for (File file : filestozip) {
				if (!file.isDirectory()) {
					addToZip(file, zos);
				}
			}

			zos.close();
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void addToZip(File file, ZipOutputStream zos) throws FileNotFoundException,
	IOException {

		FileInputStream fis = new FileInputStream(file);
		String zipFilePath = file.getPath().substring(sourcerootfiles.length(), file.getPath().length()).replace("\\", "/");
		if (debug) logger.debug("Adding '" + zipFilePath + "' to zip file");
		ZipEntry zipEntry = new ZipEntry(zipFilePath);
		zos.putNextEntry(zipEntry);

		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zos.write(bytes, 0, length);
		}

		zos.closeEntry();
		fis.close();
	}

	public static void main(String[] args) {

		org.apache.log4j.helpers.LogLog.setQuietMode(true);
		CreateRosettaCSV myself = null;

		try {
			if (System.getProperty("os.name").startsWith("Windows")) {
				windows = true;
				logwindow = new LogWindow();
				separator = "\\";
			}
			Instant startTime = Instant.now();
			myself = new CreateRosettaCSV();
			myself.get_sourcecsv();
			myself.create_targetroot();
			myself.read_csv_files();
			Instant endTime = Instant.now();
			Duration d = Duration.between(startTime, endTime);
			long hoursPart = d.toHours(); 
			long minutesPart = d.minusHours( hoursPart ).toMinutes(); 
			long secondsPart = d.minusMinutes( minutesPart ).getSeconds() ;
			long millisecondsPart = d.minusSeconds(secondsPart).toMillis(); 
			logger.info("Processing time: " + hoursPart + "h:" + minutesPart +"min:"+ secondsPart + "." + millisecondsPart + "s");
			if (windows) {
				String results_folder = get_result_folder(targetdircsv.trim());
				logwindow.showInfo("*****************************" + System.lineSeparator());
				logwindow.showInfo(" **FINISHED processing**" + System.lineSeparator());
				logwindow.showInfo("*****************************" + System.lineSeparator());
				logwindow.showInfo("Results are in folder '" + results_folder + "'." + System.lineSeparator());
				logwindow.showInfo("Log files are in folder '" + currentDir + separator + "logs'."  + System.lineSeparator());
				logwindow.showInfo(System.lineSeparator());
				if (!nfspathtostreams.isEmpty() && !zipstreamfolder) {
					logwindow.showInfo("*************************" + System.lineSeparator());
					logwindow.showInfo("NOTE: Representation folders including stream files must be uploaded to following location on the server:" + System.lineSeparator());
					logwindow.showInfo(nfspathtostreams + System.lineSeparator() + "*************************");
				}
				logwindow.showInfo(System.lineSeparator() + "Processing time: " + hoursPart + "h:" + minutesPart +"min:"+ secondsPart + "." + millisecondsPart + "s");
			}
		} catch (Exception e) 
		{
			if (myself != null)
				myself.format_exception(e);
			else {
				logger.error(e.getLocalizedMessage()); 
				if (windows) {
					logwindow.showInfo("ERROR: " + e.getLocalizedMessage() + System.lineSeparator());
				}
			}
		}
		if (myself != null)
			try {
				myself.finalize();
			} catch (Exception e) {};
	}
}
