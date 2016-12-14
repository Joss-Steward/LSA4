package FileSystemApp;

import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;

import FileSystemApp.Utils.Priority;

import java.util.Scanner;

import org.omg.CORBA.*;
import org.omg.CORBA.ORBPackage.InvalidName;

/**
 * A simple client that connects to a server and provides the user with an interactive interface
 * @author Joss Steward, Merlin
 */
public class FileSystemClient
{
	static FileSystem fileSystemImpl;

	/**
	 * Nifty little multi-line intro ascii-art, to give that nice retro feel
	 */
	static String intro =
	"  _______  _______  _______  ______   _______  \n" +
	" (  ____ \\(  ___  )(  ____ )(  ___ \\ (  ___  ) \n" +
	" | (    \\/| (   ) || (    )|| (   ) )| (   ) | \n" +
	" | |      | |   | || (____)|| (__/ / | (___) | \n" +
	" | |      | |   | ||     __)|  __ (  |  ___  | \n" +
	" | |      | |   | || (\\ (   | (  \\ \\ | (   ) | \n" +
	" | (____/\\| (___) || ) \\ \\__| )___) )| )   ( | \n" +
	" (_______/(_______)|/   \\__/|/ \\___/ |/     \\| \n";

	/**
	 * Generic help text
	 */
	static String helpText =
	"The following commands are available: \n" +
	"     open  <filename> [*read*|write]\n" +
	"     close <file_pointer (int)>\n" +
	"     read  <file_pointer (int)> <record (int)>\n" +
	"     write <file_pointer (int)> <record (int)>\n" +
	"     list  [*all*|open]\n" +
	"     exit\n\n" +
	"For more information, type: help <command>\n";

	/**
	 * Information on using the open command
	 */
	static String openHelpText =
	"OPEN - Open a file for manipulation\n" +
	"       Returns a pointer to the opened file\n" +
	"Useage: open <filename> [*read*|write]\n\n" +
	"     filename: The name of the file to open\n" +
	"     read: Open the file in read-only mode (default)\n" +
	"     write: Open the file in write-only mode\n";

	/**
	 * Information on using the close command
	 */
	static String closeHelpText =
	"CLOSE - Close an open file so it can be used elsewhere\n" +
	"Useage: close <file_pointer (int)>\n\n" +
	"     file_pointer (int): The handle you want to close\n";

	/**
	 * Information on using the read command
	 */
	static String readHelpText =
	"READ - Read a record from an open file\n" +
	"Useage: read <file_pointer (int)> <record>\n\n" +
	"     file_pointer (int): The handle to the file you want to read from\n" +
	"     record (int): The number of the record you want to read\n";

	/**
	 * Information on using the write command
	 */
	static String writeHelpText =
	"WRITE - Replace the specified record in an open file\n" +
	"Useage: write <file_pointer (int)> <record>\n\n" +
	"     file_pointer (int): The handle to the file you want to write to\n" +
	"     record (int): The number of the record you want to replace\n\n" +
	"You will be prompted for the new data\n\n";

	/**
	 * Information on using the list command
	 */
	static String listHelpText =
	"LIST - List the files stored on the server, for testing purposes\n" +
	"Useage: list [*all*|open]\n\n" +
	"     all: List all of the files held locally on the connected server (default)\n" +
	"     open: List only the currently open files on the connected server\n";

	/**
	 * Information on using the exit command
	 */
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

	/**
	 * List the local files on the server
	 * @param all When true, list all the local files. When false, list only the open files.
	 */
	public static void list(boolean all)
	{
		String files[] = fileSystemImpl.listFiles(all);

		for(int i = 0; i < files.length; i++)
		{
			System.out.println(files[i]);
		}
	}

	/**
	 * Open a file on the connected server
	 * @param filename The name of the file to open
	 * @param write When true, open the file in write mode
	 */
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

	/**
	 * Close an open file
	 * @param filePointer The handle of the file to close
	 */
	public static void close(int filePointer)
	{
		fileSystemImpl.closeFile(filePointer);
		System.out.println("Closed file '" + filePointer + "'");
	}

	/**
	 * Read a record from an open file
	 * @param filePointer The handle of the file to read from
	 * @param recordNumber The number of the record to read
	 */
	public static void read(int filePointer, int recordNumber)
	{
		String record = fileSystemImpl.readRecord(filePointer, recordNumber);
		System.out.printf("FILE %03d RECORD %04d: %s", filePointer, recordNumber, record);
	}

	/**
	 * Write the specified record to an open file.
	 * Gets the new data from the terminal.
	 * @param filePointer The handle of the file to write too
	 * @param recordNumber The number of the record to change/write
	 */
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

	/**
	 * Show a few lines of simple help text when an invalid command is entered
	 */
	public static void showInvalidCmd()
	{
		System.out.println("The command you entered is not valid, please try again.");
		System.out.println("Type 'help' to get a list of valid commands.");
		System.out.println("Type 'help <command>' for imformation on how to use a command.");
	}

	/**
	 * Parse and evaluate the give line of input
	 * @param inputLine The line of input to parse
	 * @return Returns true if the program should continue running
	 */
	public static boolean repl(String inputLine)
	{
		String[] tokens = inputLine.toLowerCase().split(" ");

		if(tokens.length == 0)
			// When there's no command, just get another line
			return true;

		if(tokens[0].equals("exit"))
			// When the command is "exit", quit
			return false;

		/**
		 * Soooo, I know this is huge and a bit obnoxious to read.
		 * I considered implementing it with something like the command pattern,
		 * but it seemed like overkill since the client wasn't the main focus of this project.
		 */
		// Help commands
		if(tokens[0].equals("help"))
		{
			if(tokens.length == 1)
			{
				// Just 'help'
				System.out.print(helpText);
			}
			else if(tokens.length == 2)
			{
				// 'help open'
				if(tokens[1].equals("open"))
				{
					System.out.print(openHelpText);
				}
				// 'help close'
				else if(tokens[1].equals("close"))
				{
					System.out.print(closeHelpText);
				}
				// 'help read'
				else if(tokens[1].equals("read"))
				{
					System.out.print(readHelpText);
				}
				// 'help write'
				else if(tokens[1].equals("write"))
				{
					System.out.print(writeHelpText);
				}
				// 'help list'
				else if(tokens[1].equals("list"))
				{
					System.out.print(listHelpText);
				}
				// 'help exit'
				else if(tokens[1].equals("exit"))
				{
					System.out.print(exitHelpText);
				}
				// 'help *'
				else
				{
					System.out.println("Unknown command: '" + tokens[1] + "'");
				}
			}
			else
			{
				// Too many params
				showInvalidCmd();
			}

			// Keep running
			return true;
		}
		// Open commands
		else if(tokens[0].equals("open"))
		{
			// 'open' commands
			if(tokens.length == 2)
			{
				// 'open <file>'
				open(tokens[1], false);
			}
			else if(tokens.length == 3)
			{
				// 'open <file> read'
				if(tokens[2].equals("read"))
				{
					// Open a file in read mode
					open(tokens[1], false);
				}
				// 'open <file> write'
				else if(tokens[2].equals("write"))
				{
					// Open a file in write mode
					open(tokens[1], true);
				}
				else
				{
					// Last option makes no sense
					showInvalidCmd();
				}
			}
			else
			{
				// Too many commands
				showInvalidCmd();
			}

			// Keep running
			return true;
		}
		// Close commands
		else if(tokens[0].equals("close"))
		{
			if(tokens.length == 2)
			{
				// 'close <file>'
				try
				{
					// Get the file pointer
					int fp = Integer.parseInt(tokens[1]);
					close(fp);
				}
				catch (Exception e)
				{
					// Couldn't parse the file pointer
					showInvalidCmd();
				}
			}
			else
			{
				// Not enough or too many commands
				showInvalidCmd();
			}

			// Keep running
			return true;
		}
		// Read commands
		else if(tokens[0].equals("read"))
		{
			if(tokens.length == 3)
			{
				// 'read <fp> <rec>'
				try
				{
					// Parse the file handle and the record number
					int fp = Integer.parseInt(tokens[1]);
					int rec = Integer.parseInt(tokens[2]);
					read(fp, rec);
				}
				catch (Exception e)
				{
					// Couldn't parse the numbers
					showInvalidCmd();
				}
			}
			else
			{
				// Not enough or too many commands
				showInvalidCmd();
			}

			// Keep running
			return true;
		}
		// Write commands
		else if(tokens[0].equals("write"))
		{
			if(tokens.length == 3)
			{
				// 'write <fp> <rec>'
				try
				{
					// Get the handle and the record number
					int fp = Integer.parseInt(tokens[1]);
					int rec = Integer.parseInt(tokens[2]);

					// write() handles getting the new line to write
					write(fp, rec);
				}
				catch (Exception e)
				{
					// Couldn't parse the parameters
					showInvalidCmd();
				}
			}
			else
			{
				// Too many or not enough parameters
				showInvalidCmd();
			}

			// Keep running
			return true;
		}
		// List commands
		else if(tokens[0].equals("list"))
		{
			if(tokens.length == 1)
			{
				// 'list'
				// Just list all the local files on the server
				list(true);
			}
			else if(tokens.length == 2)
			{
				if(tokens[1].equals("all"))
				{
					// 'list all'
					// List all the local files on the server
					list(true);
				}
				else if(tokens[1].equals("open"))
				{
					// 'list open'
					// List only the open files on the server
					list(false);
				}
				else
				{
					// Unknown command
					showInvalidCmd();
				}
			}
			else
			{
				// Not enough or too many params
				showInvalidCmd();
			}

			// Keep running
			return true;
		}

		// Default is to keep running
		return true;
	}

	/**
	 * @param args Used to initialize ORB
	 */
	public static void main(String args[])
	{
		/* Let's get a little retro, because why not */
		System.out.print(intro);
		System.out.printf("\nLSA PROJECT 4\n");
		System.out.printf("BY: Joss Steward, Drew Rife, Alec Waddelow\n\n");

		/**
		 * I couldn't find a simple way to fetch the hostname of the connected server from
		 * any of the ORB objects, so instead I just parse the parameters.
		 *
		 * If the parameter isn't found or doesn't exist, the hostname will just be null
		 */
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
			// Connect to the ORB Server
			connect(args);

			System.out.println("Connected to " + hostname);
			System.out.println("Type 'help' to list available commands\n");

			input = new Scanner(System.in);
			boolean run = true;

			// Run until we don't want to run anymore
			while(run)
			{
				// Print the prompt
				System.out.print("CONNECTED (" + hostname + ") > ");

				try
				{
					// If we get EOF (^D), break
					if(!input.hasNext()) break;

					// Otherwise, grab the next line and shove it through the REPL
					run = repl(input.nextLine());
				}
				catch (Exception e)
				{
					// If something breaks, log it to stdout
					Utils.log("\n" + e.toString(), Priority.ERROR);
				}

				System.out.println();
			}

			input.close();
			System.out.println("\nGOODBYE");
		}
		catch (Exception e)
		{
			// If something breaks, log it to stdout
			Utils.log(e.toString(), Priority.ERROR);
		}
	}
}