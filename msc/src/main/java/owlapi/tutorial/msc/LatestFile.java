package owlapi.tutorial.msc;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class LatestFile {
	public static String getLatestFile()
	{
		File f = new File("/home/sharad/Desktop/jtpFileUpload/uploads");
		File[] files = f.listFiles();
		Arrays.sort(files, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.valueOf(f1.lastModified()).compareTo(
						f2.lastModified());
			}
		});
		return files[files.length-1].getName();
	}
}
