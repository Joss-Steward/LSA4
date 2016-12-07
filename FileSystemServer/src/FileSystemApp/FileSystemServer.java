package FileSystemApp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Stream;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;


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

	private Map<Integer, String> filePointers = new HashMap<Integer, String>();
	private int keyCount = 0;

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

	@Override
	public boolean hasFile(String fileName) {
		File[] localFiles = cacheDirectory.listFiles();

		for(File f : localFiles)
		{
			// Objects.equals checks for nulls before calling .equals, just saves us a null check.
			if(Objects.equals(f.getName(), fileName))
				return true;
		}

		return false;
	}

	@Override
	public String getFile(String fileName) {
		String fileContents = "";

		Path filePath = Paths.get(fileDir + fileName);

		try {
			fileContents = new String(Files.readAllBytes(filePath), "UTF-8");
		} catch (Exception e) {
			System.out.println("Error while reading file: " + e.toString());
		}

		return fileContents;
	}

	/**
	 * In grand ol' C fashion, this returns -1 if the file doesn't exist locally.
	 * Eventually, this will also check with the other servers.
	 */
	@Override
	public int openFileReadonly(String fileName) {
		System.out.println("Opening file: " + fileName);

		try {
			if(!this.hasFile(fileName))
			{
				System.out.println("File not found locally, checking other servers: ");
				for(String host : Files.readAllLines(serverListFile))
				{
					System.out.print(host + ":");

			        Properties props = new Properties();
			        props.put("org.omg.CORBA.ORBInitialPort", "1052");
			        props.put("org.omg.CORBA.ORBInitialHost", host);

			        ORB orb = org.omg.CORBA.ORB.init(new String[0], props);
					org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
					NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

					FileSystem remoteServer = FileSystemHelper.narrow(ncRef.resolve_str("FileSystem"));

					System.out.print(" connected :");

					if(remoteServer.hasFile(fileName))
					{
						System.out.print(" file found :");

						String fileContents = remoteServer.getFile(fileName);

					    PrintWriter writer = new PrintWriter(fileDir + fileName, "UTF-8");
					    writer.write(fileContents);
						writer.close();

						System.out.println(" file written");

						break;
					} else {
						System.out.println(" file not found");
					}
				}
			} else
			{
				System.out.println("File found locally");
			}
		} catch (Exception e) {
			System.out.println("Error while reading file: " + e.toString());
		}

		keyCount++;

		filePointers.put(keyCount, fileName);
		return keyCount;
	}

	@Override
	public String readRecord(int lineNumber, int filePointer) {
		if(!filePointers.containsKey(filePointer)) {
			System.out.println("Tried to read file '" + filePointer + "' which is not valid.");
			return "";
		}

		Path filePath = Paths.get(fileDir + filePointers.get(filePointer));

		String line = "";
		Stream<String> fileLines = null;
		try {
			fileLines = Files.lines(filePath);

			line = fileLines.skip(lineNumber).findFirst().get();
		} catch (Exception e) {
			System.out.println("Error while reading file: " + e.toString());
			line = "";
		} finally {
			if(fileLines != null)
				fileLines.close();
		}

		return line;
	}

	@Override
	public void closeFile(int filePointer) {
		filePointers.remove(filePointer);
	}

	@Override
	public String[] listOpenFiles() {
		ArrayList<String> openFiles = new ArrayList<String>();

		for(Map.Entry<Integer, String> pair : filePointers.entrySet()) {
			openFiles.add(pair.getValue());
		}

		return openFiles.toArray(new String[0]);
	}

	@Override
	public String[] listLocalFiles()
	{
		File[] localFiles = cacheDirectory.listFiles();

		ArrayList<String> returnList = new ArrayList<String>();

		for(File f : localFiles)
		{
			if(!f.isDirectory())
				returnList.add(f.getName());
		}

		return returnList.toArray(new String[0]);
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
	 * @param args ignored
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

			System.out.println("FileSystemServer ready and waiting ...");
			System.out.println("Current working directory: " + Paths.get("").toAbsolutePath().toString());

			// wait for invocations from clients
			orb.run();
		}

		catch (Exception e)
		{
			System.err.println("ERROR: " + e.toString());
			//e.printStackTrace(System.out);
		}

		System.out.println("FileSystemServer Exiting ...");

	}
}
