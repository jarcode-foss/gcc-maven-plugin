package ca.jarcode.gcc.plugin;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CompilerTools {

	// these are source file extensions used by this plugin
	public static final String[] EXTENSIONS = {
			"c", "cpp"
	};

	@FunctionalInterface
	interface ShellExecutor {
		int exec(String command, Log logger, File workingDirectory) throws MojoFailureException;
	}

	public static final ShellExecutor EXECUTOR = (cmd, logger, dir) -> {
		try {
			logger.info("");
			logger.info(cmd);
			logger.info("");

			final Process process = new ProcessBuilder()
					.command(cmd.split("\0"))
					.directory(dir)
					.start();

			AtomicReference<Throwable> errResult = new AtomicReference<>(null);
			AtomicReference<Throwable> outResult = new AtomicReference<>(null);

			Thread errStreamTask = new Thread(() ->
					CompilerTools.readProcessStream(process.getErrorStream(), logger::error, errResult));

			Thread outStreamTask = new Thread(() ->
					CompilerTools.readProcessStream(process.getInputStream(), logger::info, outResult));

			errStreamTask.start();
			outStreamTask.start();

			int result = process.waitFor();
			errStreamTask.join();
			outStreamTask.join();
			Throwable errThrowable = errResult.get();
			Throwable outThrowable = outResult.get();
			if (errThrowable != null)
				throw errThrowable;
			if (outThrowable != null)
				throw outThrowable;
			logger.info("");
			return result;
		}
		catch (Throwable e) {
			e.printStackTrace();
			throw new MojoFailureException("failed to run compile command");
		}
	};

	public static void readProcessStream(InputStream stream, Consumer<String> logFunction,
	                                     AtomicReference<Throwable> result) {
		try {
			InputStreamReader reader = new InputStreamReader(stream);
			StringBuilder builder = new StringBuilder();
			int in;
			while ((in = reader.read()) != -1) {
				char c = (char) in;
				if (c == '\n') {
					logFunction.accept(builder.toString());
					builder = new StringBuilder();
				}
				else builder.append(c);
			}
			if (builder.length() > 0) {
				logFunction.accept(builder.toString());
			}
		}
		catch (Throwable e) {
			result.set(e);
		}
	}

	public static void  forkFind(File f, HashMap<String, File> m, boolean objects) {
		String path = f.getAbsolutePath();
		if (!path.endsWith(File.separator))
			path += File.separator;
		forkFind(f, m, path.length(), objects);
	}

	public static void forkFind(File f, HashMap<String, File> m, int sub, boolean objects) {
		if (!f.isDirectory())
			return;
		File[] files = f.listFiles();
		if (files == null)
			return;
		for (File file : files) {

			boolean valid = false;
			for (String suffix : EXTENSIONS) {
				if (!objects) {
					if (file.getName().endsWith("." + suffix)) {
						valid = true;
						break;
					}
				}
				else if (file.getName().endsWith(".o")) {
					valid = true;
					break;
				}
			}

			if (valid) {
				String mapping = file.getAbsolutePath().substring(sub);
				m.put(mapping, file);
				forkFind(file, m, sub, objects);
			}
		}
	}
}
