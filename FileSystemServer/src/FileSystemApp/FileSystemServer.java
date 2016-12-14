package FileSystemApp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import FileSystemApp.Utils.Priority;


/**
 * Used by both Client & Server
 * @author Joss Steward, merling
 */
class FileSystemImpl extends FileSystemPOA
{
	private ORB orb;

	// This is the working directory which contains the file cache
	private String fileDir = Paths.get("").toAbsolutePath().toString() + "/fileCache/";
	private File cacheDirectory = new File(fileDir);

	/**
	 * This file holds a list of all the other servers
	 * This file will be parsed every time other servers need to be checked, so that
	 *	 new servers can be brought up without restarting everything
	 */
	private Path serverListFile = Paths.get(Paths.get("").toAbsolutePath().toString() + "/servers.dat");

	// This enum is used to track the current status of opened files
	private enum FileStatus
	{
		OPEN_READ,
		OPEN_WRITE,
		DIRTY
	}

	// This map goes from filePointers to the filename
	private Map<Integer, String> filePointers = new HashMap<Integer, String>();

	// This map goes from a filename to the current status of the file
	private Map<String, FileStatus> fileStatus = new HashMap<String, FileStatus>();

	// This is just used so we can avoid giving out the same file handle twice
	private int handleCount = 0;

	////////////////////
	// HELPER METHODS //
	////////////////////

	/**
	 * Helper method
	 * Used when fetching a file from a remote server
	 * If this returns false, then another server probably has the file open for writing.
	 * 	Or, the file doesn't exist anywhere.
	 * @param fileName The name of the file to find.
	 * @return True if the file was found & fetched successfully, False otherwise.
	 */
	private boolean findAndPullFile(String fileName)
	{
		boolean fileAvailable = false;

		try
		{
			/**
			 * Connect to all the other servers in the serverListFile and attempt to
			 *  fetch a copy of the file.
			 * If the remote server has the file, but it cannot be fetched,
			 *  then it is probably DIRTY or OPEN_WRITE and we need to inform the user.
			 */
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

					if(fileContents.fileReturned)
					{
					    PrintWriter writer = new PrintWriter(fileDir + fileName, "UTF-8");
					    writer.write(fileContents.fileContents);
						writer.close();
						fileAvailable = true;

						Utils.log("Requesting file '" + fileName + "' from '" + host + "' [OK]");
					}
					else
					{
						fileAvailable = false;
						Utils.log("Requesting file '" + fileName + "' from '" + host + "' [FAIL]");
					}
					break;
				}
				else
				{
					Utils.log(host + " does not have file '" + fileName + "'");
				}
			}
		}
		catch (Exception e)
		{
			Utils.log(e.toString(), Priority.ERROR);
		}

		return fileAvailable;
	}

	/**
	 * Helper Method
	 * Connect to all the remote servers and inform them that we are opening a file for writing,
	 *  and thus they need to mark it DIRTY if they have it open, or delete it if they don't have it open.
	 * @param fileName The name of the file to mark dirty
	 * @return True if the file could be marked dirty, False if otherwise
	 */
	private boolean markAllDirty(String fileName)
	{
		try
		{
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
					/**
					 * If we can't mark the file dirty, then something has gone wrong
					 */
					if(!remoteServer.markDirty(fileName))
					{
						return false;
					}
				}
				else
				{
					Utils.log(host + " does not have file '" + fileName + "'");
				}
			}
		}
		catch (Exception e)
		{
			Utils.log(e.toString(), Priority.ERROR);
		}

		return true;
	}


	/////////////////
	// ORB METHODS //
	/////////////////

	public void setORB(ORB orb_val)
	{
		orb = orb_val;
	}

	////////////////////
	// SERVER METHODS //
	////////////////////

	@Override
	public void shutdown()
	{
		orb.shutdown(false);
	}

	/**
	 * Returns if a copy of the given file is stored on this server
	 */
	@Override
	public boolean hasFile(String fileName)
	{
		File[] localFiles = cacheDirectory.listFiles();

		for(File f : localFiles)
		{
			// Objects.equals checks for nulls before calling .equals, just saves us a null check.
			if(Objects.equals(f.getName(), fileName))
			{
				Utils.log("hasFile: '" + fileName + "': TRUE");
				return true;
			}
		}

		Utils.log("hasFile: '" + fileName + "': FALSE");
		return false;
	}

	/**
	 * Get a copy of the specified file from this server
	 * Returns a small class which contains the contents of the file,
	 * 	and a boolean indicating if the file was actually returned
	 */
	@Override
	public FileCopy getFile(String fileName)
	{
		FileCopy retval = new FileCopy();
		retval.fileReturned = false;

		// Most of this file handling code is pretty messy... but it works
		Path filePath = Paths.get(fileDir + fileName);

		/**
		 * If the file is currently open on this server, there will be a key in fileStatus.
		 * If the value of that key is "OPEN_READ", or no key exists, then we can return the file without issue
		 * If the value of that key is "OPEN_WRITE" or "DIRTY" then we cannot return the file
		 *  (it is locked either here or on another server), although we do have it.
		 */
		if(fileStatus.containsKey(fileName))
		{
			if(fileStatus.get(fileName) == FileStatus.OPEN_READ)
			{
				retval.fileReturned = true;
			}
		}
		else
		{
			retval.fileReturned = true;
		}

		if(retval.fileReturned)
		{
			try
			{
				retval.fileContents  = new String(Files.readAllBytes(filePath), "UTF-8");
				Utils.log("getFile: '" + fileName + "' success");
			}
			catch (Exception e)
			{
				Utils.log("getFile: '" + fileName + "' - " + e.toString(), Priority.WARNING);
			}
		}
		else
		{
			Utils.log("getFile: '" + fileName + "' cannot return contents (OPEN_WRITE or DIRTY)");
		}

		return retval;
	}

	/**
	 * Open a file for reading
	 * @return A handle to the file you opened, or -1 if the file is locked
	 */
	@Override
	public int openFileReadonly(String fileName)
	{
		/**
		 * If the file exists locally, and someone else connected to this
		 *  server has the file open, there will be a key in this dict.
		 * If the file was opened on a remote server, the file will be marked DIRTY if it is also open to read here,
		 *  or it will have been deleted if no one was using it locally.
		 */
		if(fileStatus.containsKey(fileName))
		{
			/**
			 * If the file is currently OPEN_WRITE or DIRTY then we can't open it for reading
			 */
			if(fileStatus.get(fileName) == FileStatus.OPEN_WRITE || fileStatus.get(fileName) == FileStatus.DIRTY)
			{
				Utils.log("openFileReadonly: '" + fileName + "' currently open for writes, cannot open");
				return -1;
			}
		}

		try
		{
			/**
			 * If we don't have a copy of the file, another server might. Try to find it.
			 * If we can't get a copy from any other server, it either doesn't exist or is currently locked.
			 */
			if(!this.hasFile(fileName))
			{
				Utils.log("openFileReadonly: '" + fileName + "' not found locally, checking other servers");
				boolean success = findAndPullFile(fileName);

				if(!success)
				{
					return -1;
				}
			}
			else
			{
				Utils.log("File found locally");
			}

			handleCount++;

			filePointers.put(handleCount, fileName);
			fileStatus.put(fileName, FileStatus.OPEN_READ);
		}
		catch (Exception e)
		{
			Utils.log(e.getMessage(), Priority.ERROR);
		}

		return handleCount;
	}

	/**
	 * Open a file in Read/Write mode. The logic is similar to that of openFileRead, however
	 *  this method also marks the file as dirty on all other servers, which will then delete
	 *  their local coppies immediately if they aren't in use, or after the last reader closes it.
	 */
	@Override
	public int openFileReadWrite(String fileName)
	{
		/**
		 * If the file exists locally, and someone else connected to this
		 *  server has the file open, there will be a key in this dict.
		 * If the file was opened on a remote server, the file will be marked DIRTY if it is also open to read here,
		 *  or it will have been deleted if no one was using it locally.
		 */
		if(fileStatus.containsKey(fileName))
		{
			if(fileStatus.get(fileName) == FileStatus.OPEN_WRITE || fileStatus.get(fileName) == FileStatus.DIRTY)
			{
				Utils.log("openFileReadWrite: '" + fileName + "' currently open for writes, cannot open");
				return -1;
			}
		}

		try
		{
			/**
			 * If we don't have a copy of the file, another server might. Try to find it.
			 * If we can't get a copy from any other server, it either doesn't exist or is currently locked.
			 */
			if(!this.hasFile(fileName))
			{
				Utils.log("openFileReadWrite: '" + fileName + "' not found locally, checking other servers");
				boolean success = findAndPullFile(fileName);

				if(!success)
				{
					return -1;
				}
			} else {
				Utils.log("File found locally");
			}

			/**
			 * Try to mark the file dirty everywhere. This should work unless we encounter a race
			 * condition with multipl clients opening the same file for write on different servers.
			 */
			if(markAllDirty(fileName))
			{
				if(!fileStatus.containsKey(fileName))
				{
					fileStatus.put(fileName, FileStatus.OPEN_WRITE);
				}
				else
				{
					fileStatus.replace(fileName, FileStatus.OPEN_WRITE);
				}

				handleCount++;

				filePointers.put(handleCount, fileName);
			}
			else
			{
				Utils.log("openFileReadWrite: '" + fileName + "' cannot mark dirty");
				return -1;
			}
		}
		catch (Exception e)
		{
			Utils.log(e.getMessage(), Priority.ERROR);
		}

		return handleCount;
	}

	/**
	 * Read a record from the file.
	 * Each record in these files is just a line or arbitrary length and ending with /n
	 * If a record does not exist, this will return an empty string.
	 */
	@Override
	public String readRecord(int filePointer, int recordNumber)
	{
		if(!filePointers.containsKey(filePointer))
		{
			Utils.log("readRecord: Tried to read file '" + filePointer + "' which is not valid.");
			return "";
		}

		Path filePath = Paths.get(fileDir + filePointers.get(filePointer));

		String line = "";
		Stream<String> fileLines = null;

		try
		{
			fileLines = Files.lines(filePath);
			line = fileLines.skip(recordNumber).findFirst().get();
		}
		catch (Exception e)
		{
			Utils.log("readRecord: " + e.toString(), Priority.ERROR);
			line = "";
		}
		finally
		{
			if(fileLines != null)
				fileLines.close();
		}

		return line;
	}

	/**
	 * Close the file referred to by the give file handle.
	 * If the file was marked dirty, delete the local copy.
	 */
	@Override
	public void closeFile(int filePointer)
	{
		String fileName = filePointers.get(filePointer);
		Utils.log("Closing handle " + filePointer + " ('" + fileName + "')");

		filePointers.remove(filePointer);

		// If no handles to the file are still open on this server
		if(!filePointers.containsValue(fileName))
		{
			// If the file was dirty, delete it
			if(fileStatus.get(fileName) == FileStatus.DIRTY)
			{
				Path filePath = Paths.get(fileDir + fileName);

				try
				{
					Files.deleteIfExists(filePath);
				}
				catch (IOException e)
				{
					Utils.log("Failed to delete file '" + fileName + "'", Priority.ERROR);
				}
			}
			else
			{
				fileStatus.remove(fileName);
			}
		}
		else
		{
			Utils.log("Not removing status, file still in use");
		}
	}

	/**
	 * Provides a nicely formatted list of the local files and their status.
	 * @param all If true, list all local files. If false, list only open local files.
	 * @return A list of all (or only open) local files and their current status.
	 */
	@Override
	public String[] listFiles(boolean all)
	{
		ArrayList<String> returnList = new ArrayList<String>();

		if(all)
		{
			File[] localFiles = cacheDirectory.listFiles();

			for(File f : localFiles)
			{
				if(!f.isDirectory())
				{
					if(fileStatus.containsKey(f.getName()))
					{
						if(fileStatus.get(f.getName()) == FileStatus.DIRTY)
						{
							returnList.add(String.format("%-20s %10s", f.getName(), "DIRTY"));
						}
						else if(fileStatus.get(f.getName()) == FileStatus.OPEN_WRITE)
						{
							returnList.add(String.format("%-20s %10s", f.getName(), "OPEN WRITE"));
						}
						else if(fileStatus.get(f.getName()) == FileStatus.OPEN_READ)
						{
							returnList.add(String.format("%-20s %10s", f.getName(), "OPEN READ"));
						}
					}
					else
					{
						returnList.add(String.format("%-20s", f.getName()));
					}
				}

			}
		} else
		{
			for(Map.Entry<Integer, String> pair : filePointers.entrySet())
			{
				returnList.add(String.format("%-20s %10s", pair.getValue(), "OPEN"));
			}
		}

		return returnList.toArray(new String[0]);
	}

	/**
	 * Mark a local file dirty, or delete it if no one is currently using it.
	 * @return False if the file is locked locally, but this won't be called (by my code) unless there is a race condition
	 */
	@Override
	public boolean markDirty(String fileName)
	{
		if(fileStatus.containsKey(fileName))
		{
			if(fileStatus.get(fileName) == FileStatus.OPEN_WRITE)
			{
				return false;
			}
		}
		else
		{
			Path filePath = Paths.get(fileDir + fileName);

			try
			{
				Files.deleteIfExists(filePath);
			}
			catch (IOException e)
			{
				Utils.log("Failed to delete file '" + fileName + "'", Priority.ERROR);
			}
		}

		fileStatus.replace(fileName, FileStatus.DIRTY);
		return true;
	}

	/**
	 * Write a record to the file on disk.
	 * If the record number is higher than the number of lines in the file,
	 *  new lines will be inserted.
	 * @return True on success, False on failure
	 */
	@Override
	public boolean writeRecord(int filePointer, int recordNumber, String newValue)
	{
		/**
		 * If the file isn't currently open, and in OPEN_WRITE mode, then we shouldn't let the client write to it.
		 */
		if(!filePointers.containsKey(filePointer))
		{
			Utils.log("readRecord: Tried to update file '" + filePointer + "' which is not valid.");
			return false;
		}

		if(fileStatus.get(filePointers.get(filePointer)) != FileStatus.OPEN_WRITE)
		{
			Utils.log("readRecord: Tried to update file '" + filePointer + "' without having opened it in write mode.");
			return false;
		}

		Path filePath = Paths.get(fileDir + filePointers.get(filePointer));

		try
		{
			List<String> fileLines = Files.readAllLines(filePath);
			FileWriter f = new FileWriter(filePath.toAbsolutePath().toString());

			int upperLimit = 0;

			if(recordNumber > fileLines.size() - 1)
			{
				upperLimit = recordNumber;
			}
			else
			{
				upperLimit = fileLines.size();
			}

			/**
			 * Inserts blank lines if the record number exceeds the number of records (lines) in the file
			 */
			for(int i = 0; i < upperLimit + 1; i++)
			{
				if(i == recordNumber)
				{
					f.write(newValue + "\n");
				}
				else
				{
					if(i > fileLines.size() - 1)
					{
						f.write("\n");
					}
					else
					{
						f.write(fileLines.get(i) + "\n");
					}
				}
			}

			f.close();
		}
		catch (Exception e)
		{
			Utils.log("writeRecord: " + e.toString(), Priority.ERROR);
			return false;
		}

		return true;
	}

}

/**
 * This is the class that runs on the server
 * @author merlin, Joss Steward
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

			// Log that we're up & running
			Utils.log("FileSystemServer ready and waiting ...");
			Utils.log("Current working directory: " + Paths.get("").toAbsolutePath().toString());

			// wait for invocations from clients
			orb.run();
		}
		catch (Exception e)
		{
			// Catch and log and exceptions
			Utils.log(e.toString(), Priority.ERROR);
		}

		Utils.log("FileSystemServer Exiting ...");
	}
}