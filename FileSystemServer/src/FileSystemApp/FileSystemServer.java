package FileSystemApp;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

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
	File cacheDirectory = new File("fileCache");

	// This file holds a list of all the other servers
	private static String serverListFile = "servers.dat";

	public void setORB(ORB orb_val)
	{
		orb = orb_val;
	}

	// implement shutdown() method
	@Override
	public void shutdown()
	{
		orb.shutdown(false);
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getFile(String fileName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int openFileReadonly(String fileName) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String readRecord(int lineNumber, int filePointer) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void closeFile(int filePointer) {
		// TODO Auto-generated method stub

	}

	@Override
	public String[] listOpenFiles() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] listLocalFiles()
	{
		File[] localFiles = cacheDirectory.listFiles();

		ArrayList<String> returnList = new ArrayList<String>();

		for(File f : localFiles)
		{
			if(f.isDirectory())
				returnList.add(f.getName());
		}

		return (String[]) returnList.toArray();
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

			// wait for invocations from clients
			orb.run();
		}

		catch (Exception e)
		{
			System.err.println("ERROR: " + e);
			e.printStackTrace(System.out);
		}

		System.out.println("FileSystemServer Exiting ...");

	}
}
