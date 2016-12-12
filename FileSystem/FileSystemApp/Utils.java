package FileSystemApp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {
	public enum Priority {
		INFO,
		WARNING,
		ERROR
	}

	private static String getTimeStamp () {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
	}

	public static void log(String message) {
		log(message, Priority.INFO);
	}

	public static void log(String message, Priority priority){
		String ts = getTimeStamp();
		String p = "";

		switch (priority) {
		case INFO:
			p = "INFO";
			break;
		case WARNING:
			p = "WARNING";
			break;
		case ERROR:
			p = "ERROR";
			break;
		}

		System.out.printf("%s | %7s | %s\n", ts, p, message);
	}

}
