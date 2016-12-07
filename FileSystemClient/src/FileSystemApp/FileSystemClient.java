package FileSystemApp;

import org.omg.CosNaming.*;
import org.omg.CORBA.*;

/**
 * A simple client that just gets a
 * @author Merlin
 *
 */
public class FileSystemClient
{
	static FileSystem fileSystemImpl;

	private static void testFile(String fileName) {
		System.out.println("Local Files:");
		String[] lf = fileSystemImpl.listLocalFiles();
		for(int i = 0; i < lf.length; i++) {
			System.out.println(lf[i]);
		}

		//String fileName = "test1";
		System.out.println("Opening File: " + fileName);

		int filePtr = fileSystemImpl.openFileReadonly(fileName);
		System.out.println("Returned ptr: " + filePtr);

		System.out.println("Open Files:");
		String[] of = fileSystemImpl.listOpenFiles();
		for(int i = 0; i < of.length; i++) {
			System.out.println(of[i]);
		}

		System.out.println("Reading File...");
		System.out.println("Line 0: " + fileSystemImpl.readRecord(0, filePtr));
		System.out.println("Line 1: " + fileSystemImpl.readRecord(1, filePtr));
		System.out.println("Line 4: " + fileSystemImpl.readRecord(4, filePtr));
		System.out.println("Line 29: " + fileSystemImpl.readRecord(29, filePtr));

		System.out.println("Closing File: " + fileName);
		fileSystemImpl.closeFile(filePtr);

		System.out.println("Reading File...");
		System.out.println("Line 0: " + fileSystemImpl.readRecord(0, filePtr));
		System.out.println("Line 1: " + fileSystemImpl.readRecord(1, filePtr));
		System.out.println("Line 4: " + fileSystemImpl.readRecord(4, filePtr));
		System.out.println("Line 29: " + fileSystemImpl.readRecord(29, filePtr));

		System.out.println("Open Files:");
		of = fileSystemImpl.listOpenFiles();
		for(int i = 0; i < of.length; i++) {
			System.out.println(of[i]);
		}
	}

	/**
	 * Just do each operation once
	 * @param args ignored
	 */
	public static void main(String args[])
	{
		try
		{
			// create and initialize the ORB
			ORB orb = ORB.init(args, null);

			// get the root naming context
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");

			// Use NamingContextExt instead of NamingContext. This is
			// part of the Interoperable naming Service.
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

			// resolve the Object Reference in Naming
			String name = "FileSystem";
			fileSystemImpl = FileSystemHelper.narrow(ncRef.resolve_str(name));

			System.out.println("Obtained a handle on server object: " + fileSystemImpl);

			testFile("test1");
			testFile("notLocal");

			// This is how we would shut down the server
			// fileSystemImpl.shutdown();

		} catch (Exception e)
		{
			System.out.println("ERROR : " + e.toString());
			//e.printStackTrace(System.out);
		}
	}

}
