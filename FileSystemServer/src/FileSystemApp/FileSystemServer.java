package FileSystemApp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Stream;

import org.omg.CORBA.ORB;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import FileSystemApp.Utils.Priority;


/**
 * Used by both Client & Server
 */
class FileSystemImpl extends FileSystemPOA
{
	private ORB orb;

	// Put the directory which contains the file cache in here
	private String fileDir = Paths.get("").toAbsolutePath().toString() + "/fileCache/";
	private File cacheDirectory = new File(fileDir);

	/**
	 * This file holds a list of all the other servers
	 * This file will be parsed every time other servers need to be checked, so that
	 *	 new servers can be brought up without restarting everything
	 */
	private Path serverListFile = Paths.get(Paths.get("").toAbsolutePath().toString() + "/servers.dat");

	private enum FileStatus {
		OPEN_READ,
		OPEN_WRITE,
		DIRTY
	}

	private Map<Integer, String> filePointers = new HashMap<Integer, String>();
	private Map<String, FileStatus> fileStatus = new HashMap<String, FileStatus>();

	private int handleCount = 0;

	// implement shutdown() method
	@Override
	public void shutdown()
	{
		orb.shutdown(false);
	}

	public void setORB(ORB orb_val)
	{
		orb = orb_val;
	}

	public String readFile(String title)
	{
		try
		{
			Scanner s = new Scanner(new File(title));
			StringBuffer contents = new StringBuffer("");
			while (s.hasNext())
			{
				contents.append(s.nextLine() + "\n");
			}

			s.close();
			return contents.toString();
		} catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Returns if a copy of the given file is stored on this server
	 */
	@Override
	public boolean hasFile(String fileName) {
		File[] localFiles = cacheDirectory.listFiles();

		for(File f : localFiles)
		{
			// Objects.equals checks for nulls before calling .equals, just saves us a null check.
			if(Objects.equals(f.getName(), fileName)) {
				Utils.log("hasFile: '" + fileName + "': TRUE");
				return true;
			}
		}

		Utils.log("hasFile: '" + fileName + "': FALSE");
		return false;
	}

	/**
	 * Returns a copy of a locally stored file
	 */
	@Override
	public FileCopy getFile(String fileName) {
		FileCopy retval = new FileCopy();
		retval.fileReturned = false;

		Path filePath = Paths.get(fileDir + fileName);

		if(fileStatus.containsKey(fileName)) {
			if(fileStatus.get(fileName) == FileStatus.OPEN_READ) {
				retval.fileReturned = true;
			}
		} else {
			retval.fileReturned = true;
		}

		if(retval.fileReturned) {
			try {
				retval.fileContents  = new String(Files.readAllBytes(filePath), "UTF-8");
				Utils.log("getFile: '" + fileName + "' success");
			} catch (Exception e) {
				Utils.log("getFile: '" + fileName + "' - " + e.toString(), Priority.WARNING);
			}
		} else {
			Utils.log("getFile: '" + fileName + "' cannot return contents (OPEN_WRITE or DIRTY)");
		}

		return retval;
	}

	/**
	 * If this returns false, then another server probably has the file open for writing
	 * @param fileName
	 * @return
	 */
	private boolean findAndPullFile(String fileName) {
		boolean fileAvailable = false;

		try {
			for(String host : Files.readAllLines(serverListFile))
			{
			    Properties props = new Properties();
			    props.put("org.omg.CORBA.ORBInitialPort", "1052");
			    props.put("org.omg.CORBA.ORBInitialHost", host);

			    ORB orb = org.omg.CORBA.ORB.init(new String[0], props);
				org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
				NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

				FileSystem remoteServer = FileSystemHelper.narrow(ncRef.resolve_str("FileSystem"));

				if(remoteServer.hasFile(fileName))
				{
					FileCopy fileContents = remoteServer.getFile(fileName);

					if(fileContents.fileReturned) {
					    PrintWriter writer = new PrintWriter(fileDir + fileName, "UTF-8");
					    writer.write(fileContents.fileContents);
						writer.close();
						fileAvailable = true;

						Utils.log("Requesting file '" + fileName + "' from '" + host + "' [OK]");
					} else {
						fileAvailable = false;
						Utils.log("Requesting file '" + fileName + "' from '" + host + "' [FAIL]");
					}
					break;
				} else {
					Utils.log(host + " does not have file '" + fileName + "'");
				}
			}
		} catch (Exception e) {
			Utils.log(e.toString(), Priority.ERROR);
		}

		return fileAvailable;
	}

	private boolean markAllDirty(String fileName) {
		try {
			for(String host : Files.readAllLines(serverListFile))
			{
			    Properties props = new Properties();
			    props.put("org.omg.CORBA.ORBInitialPort", "1052");
			    props.put("org.omg.CORBA.ORBInitialHost", host);

			    ORB orb = org.omg.CORBA.ORB.init(new String[0], props);
				org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
				NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

				FileSystem remoteServer = FileSystemHelper.narrow(ncRef.resolve_str("FileSystem"));

				if(remoteServer.hasFile(fileName))
				{
					if(!remoteServer.markDirty(fileName)) {
						return false;
					}
				} else {
					Utils.log(host + " does not have file '" + fileName + "'");
				}
			}
		} catch (Exception e) {
			Utils.log(e.toString(), Priority.ERROR);
		}

		return true;
	}

	/**
	 * In grand ol' C fashion, this returns -1 if the file can't be opened.
	 */
	@Override
	public int openFileReadonly(String fileName) {
		if(fileStatus.containsKey(fileName)) {
			if(fileStatus.get(fileName) == FileStatus.OPEN_WRITE
				|| fileStatus.get(fileName) == FileStatus.DIRTY) {
				Utils.log("openFileReadonly: '" + fileName + "' currently open for writes, cannot open");
				return -1;
			}
		}

		try {
			if(!this.hasFile(fileName))
			{
				Utils.log("openFileReadonly: '" + fileName + "' not found locally, checking other servers");
				boolean success = findAndPullFile(fileName);

				if(!success) {
					return -1;
				}
			} else {
				Utils.log("File found locally");
			}

			handleCount++;

			filePointers.put(handleCount, fileName);
			fileStatus.put(fileName, FileStatus.OPEN_READ);
		} catch (Exception e) {
			Utils.log(e.getMessage(), Priority.ERROR);
		}

		return handleCount;
	}

	@Override
	public int openFileReadWrite(String fileName) {
		if(fileStatus.containsKey(fileName)) {
			if(fileStatus.get(fileName) == FileStatus.OPEN_WRITE
				|| fileStatus.get(fileName) == FileStatus.DIRTY) {
				Utils.log("openFileReadWrite: '" + fileName + "' currently open for writes, cannot open");
				return -1;
			}
		}

		try {
			if(!this.hasFile(fileName))
			{
				Utils.log("openFileReadWrite: '" + fileName + "' not found locally, checking other servers");
				boolean success = findAndPullFile(fileName);

				if(!success) {
					return -1;
				}
			} else {
				Utils.log("File found locally");
			}

			if(markAllDirty(fileName)) {
				if(!fileStatus.containsKey(fileName)) {
					fileStatus.put(fileName, FileStatus.OPEN_WRITE);
				} else {
					fileStatus.replace(fileName, FileStatus.OPEN_WRITE);
				}

				handleCount++;

				filePointers.put(handleCount, fileName);
			} else {
				Utils.log("openFileReadWrite: '" + fileName + "' cannot mark dirty");
				return -1;
			}
		} catch (Exception e) {
			Utils.log(e.getMessage(), Priority.ERROR);
		}

		return handleCount;
	}

	@Override
	public String readRecord(int filePointer, int recordNumber) {
		if(!filePointers.containsKey(filePointer)) {
			Utils.log("readRecord: Tried to read file '" + filePointer + "' which is not valid.");
			return "";
		}

		Path filePath = Paths.get(fileDir + filePointers.get(filePointer));

		String line = "";
		Stream<String> fileLines = null;
		try {
			fileLines = Files.lines(filePath);

			line = fileLines.skip(recordNumber).findFirst().get();
		} catch (Exception e) {
			Utils.log("readRecord: " + e.toString(), Priority.ERROR);
			line = "";
		} finally {
			if(fileLines != null)
				fileLines.close();
		}

		return line;
	}

	@Override
	public void closeFile(int filePointer) {
		String fileName = filePointers.get(filePointer);
		Utils.log("Closing handle " + filePointer + " ('" + fileName + "')");

		filePointers.remove(filePointer);

		// If no handles to the file are still open on this server
		if(!filePointers.containsValue(fileName)) {
			// If the file was dirty, delete it
			if(fileStatus.get(fileName) == FileStatus.DIRTY) {
				Path filePath = Paths.get(fileDir + fileName);
				try {
					Files.deleteIfExists(filePath);
				} catch (IOException e) {
					Utils.log("Failed to delete file '" + fileName + "'", Priority.ERROR);
				}
			} else {
				fileStatus.remove(fileName);
			}
		} else {
			Utils.log("Not removing status, file still in use");
		}
	}

	@Override
	public String[] listFiles(boolean all)
	{
		ArrayList<String> returnList = new ArrayList<String>();

		if(all) {
			File[] localFiles = cacheDirectory.listFiles();

			for(File f : localFiles)
			{
				if(!f.isDirectory()) {
					if(fileStatus.containsKey(f.getName())) {
						if(fileStatus.get(f.getName()) == FileStatus.DIRTY) {
							returnList.add(String.format("%-20s %10s", f.getName(), "DIRTY"));
						} else if(fileStatus.get(f.getName()) == FileStatus.OPEN_WRITE) {
							returnList.add(String.format("%-20s %10s", f.getName(), "OPEN WRITE"));
						} else if(fileStatus.get(f.getName()) == FileStatus.OPEN_READ) {
							returnList.add(String.format("%-20s %10s", f.getName(), "OPEN READ"));
						}
					} else {
						returnList.add(String.format("%-20s", f.getName()));
					}
				}

			}
		} else {
			for(Map.Entry<Integer, String> pair : filePointers.entrySet()) {
				returnList.add(String.format("%-20s %10s", pair.getValue(), "OPEN"));
			}
		}

		return returnList.toArray(new String[0]);
	}

	@Override
	public boolean markDirty(String fileName) {
		if(fileStatus.containsKey(fileName)) {
			if(fileStatus.get(fileName) == FileStatus.OPEN_WRITE) {
				return false;
			}
		}

		fileStatus.replace(fileName, FileStatus.DIRTY);
		return true;
	}


	@Override
	public boolean writeRecord(int filePointer, int recordNumber, String newValue) {
		if(!filePointers.containsKey(filePointer)) {
			Utils.log("readRecord: Tried to update file '" + filePointer + "' which is not valid.");
			return false;
		}

		if(fileStatus.get(filePointers.get(filePointer)) != FileStatus.OPEN_WRITE) {
			Utils.log("readRecord: Tried to update file '" + filePointer + "' without having opened it in write mode.");
			return false;
		}

		Path filePath = Paths.get(fileDir + filePointers.get(filePointer));

		try {
			List<String> fileLines = Files.readAllLines(filePath);
			FileWriter f = new FileWriter(filePath.toAbsolutePath().toString());

			int upperLimit = 0;

			if(recordNumber > fileLines.size() - 1) {
				upperLimit = recordNumber;
			} else {
				upperLimit = fileLines.size();
			}

			for(int i = 0; i < upperLimit + 1; i++) {
				if(i == recordNumber) {
					f.write(newValue + "\n");
				} else {
					if(i > fileLines.size() - 1) {
						f.write("\n");
					} else {
						f.write(fileLines.get(i) + "\n");
					}
				}
			}

			f.close();
		} catch (Exception e) {
			Utils.log("writeRecord: " + e.toString(), Priority.ERROR);
			return false;
		}

		return true;
	}

}

/**
 * This is the class that runs on the server
 * @author merlin
 *
 */
public class FileSystemServer
{

	/**
	 * @param args used to init orb
	 */
	public static void main(String args[])
	{
		try
		{
			// create and initialize the ORB
			ORB orb = ORB.init(args, null);

			// get reference to rootpoa & activate the POAManager
			POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
			rootpoa.the_POAManager().activate();

			// create servant and register it with the ORB
			FileSystemImpl fileSystemImpl = new FileSystemImpl();
			fileSystemImpl.setORB(orb);

			// get object reference from the servant
			org.omg.CORBA.Object ref = rootpoa.servant_to_reference(fileSystemImpl);
			FileSystem href = FileSystemHelper.narrow(ref);

			// get the root naming context
			// NameService invokes the name service
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
			// Use NamingContextExt which is part of the Interoperable
			// Naming Service (INS) specification.
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

			// bind the Object Reference in Naming
			String name = "FileSystem";
			NameComponent path[] = ncRef.to_name(name);
			ncRef.rebind(path, href);

			Utils.log("FileSystemServer ready and waiting ...");
			Utils.log("Current working directory: " + Paths.get("").toAbsolutePath().toString());

			// wait for invocations from clients
			orb.run();
		}

		catch (Exception e)
		{
			Utils.log(e.toString(), Priority.ERROR);
		}

		Utils.log("FileSystemServer Exiting ...");

	}
}
