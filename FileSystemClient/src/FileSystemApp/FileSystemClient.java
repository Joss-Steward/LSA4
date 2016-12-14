package FileSystemApp;

import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;

import FileSystemApp.Utils.Priority;

import java.util.Scanner;
import java.util.StringTokenizer;

import org.omg.CORBA.*;
import org.omg.CORBA.ORBPackage.InvalidName;

/**
 * A simple client that just gets a
 * @author Merlin
 *
 */
public class FileSystemClient
{
	static FileSystem fileSystemImpl;

	/* I wish java had string literals... */
	static String intro =
	"  _______  _______  _______  ______   _______  \n" +
	" (  ____ \\(  ___  )(  ____ )(  ___ \\ (  ___  ) \n" +
	" | (    \\/| (   ) || (    )|| (   ) )| (   ) | \n" +
	" | |      | |   | || (____)|| (__/ / | (___) | \n" +
	" | |      | |   | ||     __)|  __ (  |  ___  | \n" +
	" | |      | |   | || (\\ (   | (  \\ \\ | (   ) | \n" +
	" | (____/\\| (___) || ) \\ \\__| )___) )| )   ( | \n" +
	" (_______/(_______)|/   \\__/|/ \\___/ |/     \\| \n";

	static String helpText =
	"The following commands are available: \n" +
	"     open  <filename> [*read*|write]\n" +
	"     close <file_pointer (int)>\n" +
	"     read  <file_pointer (int)> <record (int)>\n" +
	"     write <file_pointer (int)> <record (int)>\n" +
	"     list  [*all*|open]\n" +
	"     exit\n\n" +
	"For more information, type: help <command>\n";

	static String openHelpText =
	"OPEN - Open a file for manipulation\n" +
	"       Returns a pointer to the opened file\n" +
	"Useage: open <filename> [*read*|write]\n\n" +
	"     filename: The name of the file to open\n" +
	"     read: Open the file in read-only mode (default)\n" +
	"     write: Open the file in write-only mode\n";

	static String closeHelpText =
	"CLOSE - Close an open file so it can be used elsewhere\n" +
	"Useage: close <file_pointer (int)>\n\n" +
	"     file_pointer (int): The handle you want to close\n";

	static String readHelpText =
	"READ - Read a record from an open file\n" +
	"Useage: read <file_pointer (int)> <record>\n\n" +
	"     file_pointer (int): The handle to the file you want to read from\n" +
	"     record (int): The number of the record you want to read\n";

	static String writeHelpText =
	"WRITE - Replace the specified record in an open file\n" +
	"Useage: write <file_pointer (int)> <record>\n\n" +
	"     file_pointer (int): The handle to the file you want to write to\n" +
	"     record (int): The number of the record you want to replace\n\n" +
	"You will be prompted for the new data\n\n";

	static String listHelpText =
	"LIST - List the files stored on the server, for testing purposes\n" +
	"Useage: list [*all*|open]\n\n" +
	"     all: List all of the files held locally on the connected server (default)\n" +
	"     open: List only the currently open files on the connected server\n";

	static String exitHelpText =
	"EXIT - Immediately quit & disconnect from the server\n";

	static String hostname;
	static Scanner input;

	/**
	 * Sets up the connection to the server specified in args
	 * @param args The args passed to the program from the command line
	 * @throws InvalidName
	 * @throws NotFound
	 * @throws CannotProceed
	 * @throws org.omg.CosNaming.NamingContextPackage.InvalidName
	 */
	public static void connect (String args[])
			throws 	InvalidName, NotFound, CannotProceed, org.omg.CosNaming.NamingContextPackage.InvalidName
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
	}

	public static void list(boolean all)
	{
		String files[] = fileSystemImpl.listFiles(all);

		for(int i = 0; i < files.length; i++)
		{
			System.out.println(files[i]);
		}
	}

	public static void open(String filename, boolean write)
	{
		int filePtr = -1;

		if(!write)
		{
			filePtr = fileSystemImpl.openFileReadonly(filename);
		}
		else
		{
			filePtr = fileSystemImpl.openFileReadWrite(filename);
		}

		if(filePtr == -1)
		{
			System.out.println("Attempt to open file failed!");
			System.out.println("Is someone else using the file?");
			System.out.println("Make sure the file exists on at least 1 server.");
		}
		else
		{
			if(write)
			{
				System.out.println("Successfully opened file '" + filename + "' in write mode");
			}
			else
			{
				System.out.println("Successfully opened file '" + filename + "' in read mode");
			}

			System.out.println("Your file handle: " + filePtr);
		}
	}

	public static void close(int filePointer)
	{
		fileSystemImpl.closeFile(filePointer);
		System.out.println("Closed file '" + filePointer + "'");
	}

	public static void read(int filePointer, int recordNumber)
	{
		String record = fileSystemImpl.readRecord(filePointer, recordNumber);
		System.out.printf("FILE %03d RECORD %04d: %s", filePointer, recordNumber, record);
	}

	public static void write(int filePointer, int recordNumber)
	{
		System.out.print("Enter new value: ");
		String newValue = input.nextLine();

		if(fileSystemImpl.writeRecord(filePointer, recordNumber, newValue))
		{
			System.out.printf("UPDATED FILE %03d RECORD %04d: %s", filePointer, recordNumber, newValue);
		}
		else
		{
			System.out.printf("FAILED TO UPDATE FILE\n");
		}
	}

	public static void showInvalidCmd()
	{
		System.out.println("The command you entered is not valid, please try again.");
		System.out.println("Type 'help' to get a list of valid commands.");
		System.out.println("Type 'help <command>' for imformation on how to use a command.");
	}

	/**
	 * Parses and interprets entered commands
	 * @param input
	 * @return
	 */
	public static boolean repl(String input)
	{
		String[] tokens = input.toLowerCase().split(" ");

		if(tokens.length == 0)
			return true;

		if(tokens[0].equals("exit"))
			return false;

		if(tokens[0].equals("help"))
		{
			if(tokens.length == 1)
			{
				System.out.print(helpText);
			}
			else if(tokens.length == 2)
			{
				if(tokens[1].equals("open"))
				{
					System.out.print(openHelpText);
				}
				else if(tokens[1].equals("close"))
				{
					System.out.print(closeHelpText);
				}
				else if(tokens[1].equals("read"))
				{
					System.out.print(readHelpText);
				}
				else if(tokens[1].equals("write"))
				{
					System.out.print(writeHelpText);
				}
				else if(tokens[1].equals("list"))
				{
					System.out.print(listHelpText);
				}
				else if(tokens[1].equals("exit"))
				{
					System.out.print(exitHelpText);
				}
				else
				{
					System.out.println("Unknown command: '" + tokens[1] + "'");
				}
			}
			else
			{
				showInvalidCmd();
			}

			return true;
		}
		else if(tokens[0].equals("open"))
		{
			if(tokens.length == 2)
			{
				open(tokens[1], false);
			}
			else if(tokens.length == 3)
			{
				if(tokens[2].equals("read"))
				{
					open(tokens[1], false);
				}
				else if(tokens[2].equals("write"))
				{
					open(tokens[1], true);
				}
				else
				{
					showInvalidCmd();
				}
			}
			else
			{
				showInvalidCmd();
			}

			return true;
		}
		else if(tokens[0].equals("close"))
		{
			if(tokens.length == 2)
			{
				try
				{
					int fp = Integer.parseInt(tokens[1]);
					close(fp);
				}
				catch (Exception e)
				{
					showInvalidCmd();
				}
			}
			else
			{
				showInvalidCmd();
			}

			return true;
		}
		else if(tokens[0].equals("read"))
		{
			if(tokens.length == 3)
			{
				try
				{
					int fp = Integer.parseInt(tokens[1]);
					int rec = Integer.parseInt(tokens[2]);
					read(fp, rec);
				}
				catch (Exception e)
				{
					showInvalidCmd();
				}
			}
			else
			{
				showInvalidCmd();
			}

			return true;
		}
		else if(tokens[0].equals("write"))
		{
			if(tokens.length == 3)
			{
				try
				{
					int fp = Integer.parseInt(tokens[1]);
					int rec = Integer.parseInt(tokens[2]);
					write(fp, rec);
				}
				catch (Exception e)
				{
					showInvalidCmd();
				}
			}
			else
			{
				showInvalidCmd();
			}

			return true;
		}
		else if(tokens[0].equals("list"))
		{
			if(tokens.length == 1)
			{
				list(true);
			}
			else if(tokens.length == 2)
			{
				if(tokens[1].equals("all"))
				{
					list(true);
				}
				else if(tokens[1].equals("open"))
				{
					list(false);
				}
				else
				{
					showInvalidCmd();
				}
			}
			else
			{
				showInvalidCmd();
			}

			return true;
		}

		return true;
	}

	/**
	 * Just do each operation once
	 * @param args ignored
	 */
	public static void main(String args[])
	{
		/* Let's get a little retro, because why not */
		System.out.print(intro);
		System.out.printf("\nLSA PROJECT 4\n");
		System.out.printf("BY: Joss Steward, Drew Rife, Alec Waddelow\n\n");

		for(int i = 0; i < args.length; i++)
		{
			if(args[i].equals("-ORBInitialHost"))
			{
				if(args.length - 1 > i)
				{
					hostname = args[i + 1];
					break;
				}
			}
		}

		try
		{
			connect(args);

			System.out.println("Connected to " + hostname);
			System.out.println("Type 'help' to list available commands\n");

			input = new Scanner(System.in);
			boolean run = true;

			while(run)
			{
				if(fileSystemImpl != null)
				{
					System.out.print("CONNECTED (" + hostname + ") > ");
				}

				try
				{
					if(!input.hasNext()) break;
					run = repl(input.nextLine());
				}
				catch (Exception e)
				{
					Utils.log("\n" + e.toString(), Priority.ERROR);
				}

				System.out.println();
			}

			input.close();
			System.out.println("\nGOODBYE");
		}
		catch (Exception e)
		{
			Utils.log(e.toString(), Priority.ERROR);
		}
	}
}