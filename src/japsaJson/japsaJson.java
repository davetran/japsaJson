package japsaJson;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/* Google GSON imports */
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author David Tran
 * @version 1.02
 * @since 29-OCT-2016
 * 
 */
public class japsaJson {
    private final WatchService watcher;
    private final Path inputDir;
    private final Path outputDir;
	
    // TODO: Check why this type of casting is required.
    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }
    
    /**
     * Creates a WatchService and registers the given directory for events
     */
    japsaJson(Path inputDir, Path outputDir) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.inputDir = inputDir;	// Set the input directory path
        this.outputDir = outputDir; // Set the output directory path
        
        //Register the input path with the watch service and monitor for events
        inputDir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    }
	
    /**
     * Process events monitored by the WatchService
     */
	void processEvents(){
		while (true) {

			WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            if (inputDir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }
            
            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                
                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path inputPath = ev.context();   
                Path child = inputDir.resolve(inputPath);
                
                // print out event
                System.out.format("%s: %s\n", event.kind().name(), child);
 
                // Watch the directory and then wait for either the creation of
                // modification of a file matching the pattern *.out.fasta.
                if ((kind == ENTRY_CREATE || kind == ENTRY_MODIFY)
                		&& inputPath.toString().endsWith(".fin.japsa")) {
                	System.out.println("Japsa Modified");
                	
                	try {
                		// Try putting the thread to sleep to prevent the watcher
                		// from triggering multiple times in the event of a combined
                		// Create and modify event.
                		Thread.sleep(1500);
                		System.out.println("Sleeping");
                	}
                	catch (InterruptedException InterruptE) {
                		// Do nothing
                	}
                	parse(child);
                }
            }
            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
            	break;
            }
		}
	}
	
	/**
	 * 
	 */
	static void usage() {
	}
	
	/**
	 * Use the input arguments to get the input and output directories.
	 * Checks if the argument paths exists and are directories.
	 * Launch the Watch Service API to monitor for changes in the input directory.
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		String WorkingDirectory = System.getProperty("user.dir");
		Path inputDir = Paths.get(WorkingDirectory);
		Path outputDir = Paths.get(WorkingDirectory);
		
		if (args.length != 0) {
			for (String s: args) {
				System.out.println(s);
			}
		}
		
		// Register the directory to be monitored
		if (args.length == 0) {
			System.out.println("No arguments entered. Will monitor the current directory.");
			System.out.format("Monitoring directory: " + WorkingDirectory);
		} else {
			inputDir = Paths.get(args[1]);
			outputDir = Paths.get(args[3]);
		}
		
		checkPath(inputDir);
		checkPath(outputDir);
		
		// Output the absolute path instead of the relative path
		System.out.format("Monitoring directory: %s%n", inputDir.toAbsolutePath().toString());
		System.out.format("Output directory: %s%n", outputDir.toAbsolutePath().toString());
		// By this stage only the existing input directory should be monitored
		new japsaJson(inputDir, outputDir).processEvents();
	}
	
	public static void checkPath(Path dir) {
		// Check if the user specified directory exists and is not a file.
		// Input path only.
		// TODO extend functionality to the output path.
		try {
			File f = dir.toFile();
			if (!f.exists()) {
				throw new NoSuchFileException(null);
			} 
			else if (!f.isDirectory()) {
				throw new NotDirectoryException(null);
			}
		}
		catch (NoSuchFileException n) {
			System.err.format("Directory does not exist: %s%n", dir.toAbsolutePath().toString());
			System.exit(1);
		}
		catch (NotDirectoryException d) {
			System.err.format("Not a directory: %s%n", dir.toAbsolutePath().toString());
			System.exit(1);
		}
	}
	
	/**
	 * Process the output from npScarf
	 * 
	 * @param dir
	 */
	public void parse(Path dir) {
		List<String> scaffoldList = new ArrayList<String>();
		
		try (Stream<String> lines = Files.lines(dir)) {
			/* Process the stream by filtering out the header lines
			 * and storing each line into a list 
			 */
			scaffoldList = lines.filter(s -> s.startsWith(">") && !s.startsWith(">>"))
            	.collect(Collectors.toList());
		}
		catch (IOException e) {
			// Do nothing (Silently fail)
			// TODO add exception handling
		}
		
		/* Identify the size of the list */
		int scaffoldTotal = scaffoldList.size();
		System.out.println("Size: " + scaffoldTotal);
		
		int scaffoldType = 0; // Linear = 0, Circular = 0;
		
		int sequenceLength = 0;
		int circularStart = 0;
		String nextSequence = new String("");
		String currentSequence = new String("");
		
		List<Map<String, Object>> listMap = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> nodeMap = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> linkMap = new ArrayList<Map<String, Object>>();
		
		Map<String, Integer> nodeCount = new HashMap<String, Integer>();

		// TODO: Clean up this block of code
		
		// Debug
		for(String s: scaffoldList) {
			System.out.format("%s%n",s);
		}
		
		// Use a normal for loop to take advantage of the index variable.
		for(int i = 0; i< scaffoldTotal; i++) {
			Map<String, Object> m = new HashMap<String, Object>();
			Map<String, Object> nm = new HashMap<String, Object>();  //3' node id
			Map<String, Object> nm2 = new HashMap<String, Object>(); //5' node id
			Map<String, Object> lm = new HashMap<String, Object>();
						
			String s = scaffoldList.get(i);
			
			// State-machine for linear or circular scaffolds.
			if(s.indexOf(">A:") != 0) {
				if(s.indexOf("Linear") != -1) {
					scaffoldType = 0;
				}
				if(s.indexOf("Circular") != -1) {
					scaffoldType = 1;
					circularStart = i + 1;	// Sequence index for the start of the circular scarf
				}	
			} //End state-machine code.

			if(s.contains(">A") == false) {
				//System.out.println(s);
				currentSequence = getSequenceName(s);
				System.out.println(currentSequence);
				//System.out.println(currentSequence);

				// Check for duplicate (repetitive) nodes
				if (countDuplicateNodes(currentSequence, nodeCount, 1) != 0) {
					currentSequence = currentSequence + "_" + 
							countDuplicateNodes(currentSequence, nodeCount,0);	
				}
				
				
				// Add to the node list
				nm.put("id", currentSequence);
				nm2.put("id", currentSequence + "'");
				
				nodeMap.add(nm);
				nodeMap.add(nm2);
				
				//System.out.println(nodeMap.toString());
				
				// Parse and add the contig data
				sequenceLength = getLength(s);
				//System.out.println(sequenceLength);
				
				m.put("source", currentSequence);
				m.put("target", currentSequence + "'");
				m.put("length", sequenceLength);
				
				listMap.add(m);
				
				/*
				 * ContigLinks code
				 */
				String nextHeader = new String("");
				String orientation = new String("");
				
				if (i < (scaffoldTotal - 1)) {
					nextHeader = scaffoldList.get(i + 1);
					if(nextHeader.contains(">A") == false) {
						nextSequence = getSequenceName(nextHeader);
						System.out.println("NExt Sequence: " + nextSequence);
						
						// We look ahead into the next line and append count suffix if required
						if (nodeCount.containsKey(nextSequence)) {
							nextSequence = nextSequence + "_" + (nodeCount.get(nextSequence)+1);
						}
						System.out.println("Modified NExt Sequence: " + nextSequence);
						
						orientation = getOrientation(s,nextHeader);
						System.out.println(orientation);
						lm = buildLinkMap(currentSequence, nextSequence, orientation);
						linkMap.add(lm);	// Add new entry for Json
					}
					else {
						// If the scaffold type is circular, connect the last sequence
			            // to the first.
						if(scaffoldType == 1) {
							nextSequence = getSequenceName(scaffoldList.get(circularStart));
							orientation = getOrientation(s,scaffoldList.get(circularStart));
							lm = buildLinkMap(currentSequence, nextSequence, orientation);
							linkMap.add(lm);	// Add new entry for Json
						}
					}
				} // END Contiglinks code
			} // END of sequence processing
		} // END of Parse function
		
		// Parsing completed, now all that is left is to build the final
		// JSON object and write the JSON output
		Map<String, Object> n = new HashMap<String, Object>();
		n.put("contigData", listMap);
		n.put("nodes", nodeMap);
		n.put("contigLinks", linkMap);
		
		// Escape certain characters
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		
		//try (FileWriter f = new FileWriter("/home/dave/Test/assembly.json")) {
		try (FileWriter f = new FileWriter(outputDir.toString() + "/assembly.json")) {

			gson.toJson(n, f);
		}
		catch (IOException e ) {
			System.out.println("Output error");
		}

	} //End parse function.
	
	public String getSequenceName(String s){
		int nodeStartIndex = 0;
		int nodeEndIndex = 0;
		
		if(s.contains("NODE")) {	//Process the NODES
			nodeStartIndex = s.indexOf("NODE");
			nodeEndIndex = s.indexOf("_length");
			//System.out.format("Node: %s%n",s);
			s = s.substring(nodeStartIndex, nodeEndIndex);
			//System.out.format("%s%n: ", s);
		}
		// Process the (transient?) nanopore read data
		else if (s.contains("_channel")){
			nodeEndIndex = s.indexOf("_channel");
			//System.out.format("Other node: %s%n", s);
			s = s.substring(1, nodeEndIndex);
			//System.out.format("Node: %s%n",s);
		}
		return s;
	}
	
	public int getLength(String s) {
		int lengthStartIndex = 0;
		int lengthDelimitIndex = 0;
		int lengthEndIndex = 0;
		int length = 0;
		
		if(s.contains("NODE")) {	//Process the NODES
			// Process length
			lengthStartIndex = s.indexOf("_length");
			lengthEndIndex = s.indexOf("_cov");
			length = stringToInteger(s.substring(lengthStartIndex + 8, lengthEndIndex));
			//System.out.format("Length: %d%n", length);
		}
		else if (s.contains("_channel")){
			lengthStartIndex = s.indexOf("(");
			lengthDelimitIndex = s.indexOf(",");
			lengthEndIndex = s.indexOf(")");
			//System.out.println("Begin: " + s.substring(lengthStartIndex + 1, lengthDelimitIndex));
			//System.out.println("End: " + s.substring(lengthDelimitIndex + 1, lengthEndIndex));
			length = stringToInteger(s.substring(lengthDelimitIndex + 1, lengthEndIndex)) -
					stringToInteger(s.substring(lengthStartIndex + 1, lengthDelimitIndex));
		}
		return length;
	}
	
	/**
	 *  Helper for getLength()
	 * @param s
	 * @return
	 */
	public int stringToInteger(String s) {
		int l = 0;
		try {
			l = Integer.valueOf(s);
		} 
		catch (NumberFormatException e) {
			l = 100;	// Edge of length zero should not exist.
		}
		return l;
	}
	
	/**
	 *
	 * 
	 * Build the ContigLinks array that will store the adjacent sequences.
	 * There are 4-possible combinations for contig links:
	 * 		Case 1:	>EDGE_A:EDGE_B;
	 * 				5'===>3'----5'===>3' 
	 * 				Specifies the EDGE_A 3' to EDGE_B 5' link
	 * 		Case 2:	>EDGE_A:EDGE_B';
	 * 				5'===>3'----3'<===5' 
	 * 				Specifies the EDGE_A 3' to EDGE_B 3' link
	 * 		Case 3:	>EDGE_A':EDGE_B;
	 * 				3'<===5'----5'===>3' 
	 * 				Specifies the EDGE_A 5' to EDGE_B 5' link
	 *  	Case 4:	>EDGE_A':EDGE_B';
	 * 				3'<===5'----3'<===5' 
	 * 				Specifies the EDGE_A 3' to EDGE_B 5' link
	 * 
	 * Internal contig representation:
	 * 		Node_X'               Node_X
	 * 		Prime end             Not prime
	 *      5'                    3'
	 *      o-------------------->o
	 * @param currentSequence
	 * @param nextSequence
	 * @return
	 */
	public Map<String, Object> buildLinkMap(String currentSequence, String nextSequence, String orientation) {
		Map<String, Object> m = new HashMap<String, Object>();
		// Check the source string
		if(orientation.charAt(0) == '+') {
			m.put("source", currentSequence);
		}
		else if (orientation.charAt(0) == '-') {
			m.put("source", currentSequence + "'");
		}
		// Check the target string
		if(orientation.charAt(1) == '+') {
			m.put("target", nextSequence + "'");
		}
		else if (orientation.charAt(1) == '-') {
			m.put("target", nextSequence);
		}
		System.out.println(m.toString());
		return m;
	}
	
	public String getOrientation (String s, String r) {
		String s0 = new String ("");
		String s1 = new String ("");
		System.out.println("Current Header: " + s);
		System.out.println("Next HEader" + r);
		if(s.contains("+(") || s.contains("+[")) {
			s0 = "+";
		}
		else if ((s.contains("-(") || s.contains("-["))) {
			s0 = "-";
		}
		// Check the target string
		if(r.contains("+(") || r.contains("+[")) {
			s1 = "+";
		}
		else if ((r.contains("-(") || r.contains("-["))) {
			s1 = "-";
		}
		return (s0 + s1);
	}
	
	/**
	 * 
	 * Count from 0 (Zero-based numbering)
	 * 
	 * @param s
	 * @param nodeCount
	 * @param flag
	 * @return
	 */
	public int countDuplicateNodes(String s, Map<String, Integer> nodeCount, int flag) {
		// Check for duplicate (repetitive) nodes
		int count = 0;
		if (flag == 1) {
			if(nodeCount.containsKey(s)) {
				count = nodeCount.get(s);
				count++;
				nodeCount.put(s, count);
				s = s + "_" + count;
				System.out.println("Duplicated node" + s);
			}
			else {
				nodeCount.put(s, 0); // Zero-based numbering.
				count = 0;	// Redundant
				System.out.println("Adding node: " + s);
			}
		} // End if
		// Flag equals any number other than 1;
		else {
			if(nodeCount.containsKey(s)) {
				count = nodeCount.get(s);
			}
			else {
				count = 0;	// Redundant
			}
		} // End else
		System.out.format("Count: %d\n", count);
		return count;
	}
}