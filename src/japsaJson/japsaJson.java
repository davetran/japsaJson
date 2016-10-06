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
 * @version 1.00
 * @since 06-OCT-2016
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
                	System.out.println("Modified");
                	
                	try {
                		Thread.sleep(500);
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
		
		// By this stage only the existing input directory should be monitored
		new japsaJson(inputDir, outputDir).processEvents();
		// Output the absolute path instead of the relative path
		System.out.format("Monitoring directory: %s%n", inputDir.toAbsolutePath().toString());
		System.out.format("Output directory: %s%n", outputDir.toAbsolutePath().toString());
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
			scaffoldList = lines.filter(s -> s.startsWith("#JSA"))
            	.collect(Collectors.toList());
		}
		catch (IOException e) {
			// Do nothing (Silently fail)
			// TODO add exception handling
		}
		
		/* Identify the size of the list */
		System.out.println("Size: " + scaffoldList.size());
		
		int scaffoldStartIndex = 0;
		int scaffoldEndIndex = 0;
		int lengthEndIndex = 0;
		
		//JsonArrayBuilder test2 = Json.createArrayBuilder();
		List<Map<String, Object>> listMap = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> nodeMap = new ArrayList<Map<String, Object>>();
		
		// TODO: Clean up this block of code
		for(String s: scaffoldList) {
			Map<String, Object> m = new HashMap<String, Object>();
			Map<String, Object> nm = new HashMap<String, Object>();
			Map<String, Object> nm2 = new HashMap<String, Object>();
			System.out.format("%s%n",s);
			scaffoldStartIndex = s.indexOf("Scaffold");
			scaffoldEndIndex = s.indexOf(":", scaffoldStartIndex);
			System.out.format("%s%n",s.substring(scaffoldStartIndex, scaffoldEndIndex));
			lengthEndIndex = s.indexOf(":", scaffoldEndIndex + 1);
			System.out.format("%s%n",s.substring(scaffoldEndIndex + 1, lengthEndIndex));
			m.put("source", s.substring(scaffoldStartIndex, scaffoldEndIndex));
			m.put("target", s.substring(scaffoldStartIndex, scaffoldEndIndex) + "'");
			nm.put("id", s.substring(scaffoldStartIndex, scaffoldEndIndex));
			nm2.put("id", s.substring(scaffoldStartIndex, scaffoldEndIndex) + "'");
			int l =0;
			try {
				l = Integer.valueOf(s.substring(scaffoldEndIndex + 1, lengthEndIndex));
			} catch (NumberFormatException e) {
				l = 0;	// Edge of length zero should not exist.
			}
			m.put("length", l);
			listMap.add(m);
			nodeMap.add(nm);
			nodeMap.add(nm2);
		}
		
		for(Map<String, Object> m : listMap) {
			System.out.println(m.toString());
		};
		Map<String, Object> n = new HashMap<String, Object>();
		n.put("contigData", listMap);
		n.put("nodes", nodeMap);
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		
		//try (FileWriter f = new FileWriter("/home/dave/Test/assembly.json")) {
		try (FileWriter f = new FileWriter(outputDir.toString() + "/assembly2.json")) {

			gson.toJson(n, f);
		}
		catch (IOException e ) {
			System.out.println("Output error");
		}

	} //End parse function.
}