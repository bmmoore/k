package org.kframework.kast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.kframework.backend.maude.MaudeFilter;
import org.kframework.backend.unparser.IndentationOptions;
import org.kframework.backend.unparser.KastFilter;
import org.kframework.compile.FlattenModules;
import org.kframework.compile.transformers.AddTopCellConfig;
import org.kframework.kil.ASTNode;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.exceptions.TransformerException;
import org.kframework.parser.ProgramLoader;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.file.KPaths;
import org.kframework.utils.general.GlobalSettings;
import org.kframework.utils.OptionComparator;

public class KastFrontEnd {

	private static final String USAGE = "kast [options] <file>" + System.getProperty("line.separator");
	private static final String HEADER_STANDARD = "";
	private static final String FOOTER_STANDARD = "";
	private static final String HEADER_EXPERIMENTAL = "Experimental options:";
	private static final String FOOTER_EXPERIMENTAL = System.getProperty("line.separator") + "These options are non-standard and subject to change without notice.";
	public static void printUsageS(KastOptionsParser op) {
		org.kframework.utils.Error.helpMsg(USAGE, HEADER_STANDARD, FOOTER_STANDARD, op.getOptionsStandard(), new OptionComparator(op.getOptionList()));
	}
	public static void printUsageE(KastOptionsParser op) {
		org.kframework.utils.Error.helpMsg(USAGE, HEADER_EXPERIMENTAL, FOOTER_EXPERIMENTAL, op.getOptionsExperimental(), new OptionComparator(op.getOptionList()));
	}

	public static void kast(String[] args) {
		Context context = new Context();
		Stopwatch sw = new Stopwatch();
		KastOptionsParser op = new KastOptionsParser();
		CommandLine cmd = op.parse(args);
		if (cmd == null) {
			printUsageS(op);
			System.exit(1);
		}

		if (cmd.hasOption("help")) {
			printUsageS(op);
			System.exit(0);
		}
		if (cmd.hasOption("help-experimental")) {
			printUsageE(op);
			System.exit(0);
		}

		if (cmd.hasOption("version")) {
			String msg = FileUtil.getFileContent(KPaths.getKBase(false) + KPaths.VERSION_FILE);
			System.out.println(msg);
			System.exit(0);
		}

		// set verbose
		if (cmd.hasOption("verbose")) {
			GlobalSettings.verbose = true;
		}

		// set fast kast option
		if (cmd.hasOption("fast-kast")) {
			GlobalSettings.fastKast = true;
		}

		String pgm = null;
		String path;

		if (cmd.hasOption("expression")) {
			pgm = cmd.getOptionValue("expression");
			path = "Command line";
		} else {
			{
				String[] restArgs = cmd.getArgs();
				if (restArgs.length < 1) {
					String msg = "You have to provide a file in order to kast a program!.";
					GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, "command line", "System file."));
				} else
					pgm = restArgs[0];
			}
			File mainFile = new File(pgm);
			path = mainFile.getAbsolutePath();
			if (!mainFile.exists())
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "Could not find file: " + pgm, "command line", "System file."));
			pgm = FileUtil.getFileContent(mainFile.getAbsolutePath());
		}

		File def = null;
		org.kframework.kil.Definition javaDef = null;
		String directory;
		if (cmd.hasOption("directory")) {
			directory = new File(cmd.getOptionValue("directory")).getAbsolutePath();
		} else {
			directory = new File(System.getProperty("user.dir")).getAbsolutePath();
		}
		{
			// search for the definition
			File[] dirs = new File(directory).listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File current, String name) {
					return new File(current, name).isDirectory();
				}
			});

			for (int i = 0; i < dirs.length; i++) {
				if (dirs[i].getAbsolutePath().endsWith("-kompiled")) {
					if (context.kompiled != null) {
						String msg = "Multiple compiled definitions found.";
						GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, "command line", new File(".").getAbsolutePath()));
					} else {
						context.kompiled = dirs[i];
					}
				}
			}
			if (context.kompiled == null && System.getenv("KRUN_COMPILED_DEF") != null) {
				context.kompiled = new File(System.getenv("KRUN_COMPILED_DEF"));
			}

			if (context.kompiled == null) {
				String msg = "Could not find a compiled definition. Use --directory to specify one.";
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, "command line", new File(".").getAbsolutePath()));
			}
		}
		try {
			if (context.kompiled.exists()) {
				File defXml = new File(context.kompiled.getCanonicalPath() + "/defx-maude.bin");
				if (!defXml.exists()) {
					GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "Could not find the compiled definition.", "command line",
							defXml.getAbsolutePath()));
				}

				javaDef = (org.kframework.kil.Definition) BinaryLoader.fromBinary(new FileInputStream(defXml));
				javaDef = new FlattenModules(context).compile(javaDef, null);
				javaDef = (org.kframework.kil.Definition) javaDef.accept(new AddTopCellConfig(
                        context));
				// This is essential for generating maude
				javaDef.preprocess(context);

				def = new File(javaDef.getMainFile());
			} else {
				String msg = "Could not find a valid compiled definition. Use --directory to specify one.";
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, "command line", new File(".").getAbsolutePath()));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}

		boolean prettyPrint = false;
		boolean nextline = false;
		IndentationOptions indentationOptions = new IndentationOptions();
		if (cmd.hasOption("pretty")) {
			prettyPrint = true;
			if (cmd.hasOption("tabsize")) {
				indentationOptions.setTabSize(new Integer(cmd.getOptionValue("tabsize")));
			} else {
				indentationOptions.setTabSize(4);
			}
			if (cmd.hasOption("maxwidth")) {
				indentationOptions.setWidth(new Integer(cmd.getOptionValue("maxwidth")));
			} else {
				indentationOptions.setWidth(Integer.MAX_VALUE);
			}
			if (cmd.hasOption("aux-tabsize")) {
				indentationOptions.setAuxTabSize(new Integer(cmd.getOptionValue("auxtabsize")));
			}
			if (cmd.hasOption("nextline")) {
				nextline = true;
			}
		}

		if (cmd.hasOption("parser")) {
			String parser = cmd.getOptionValue("parser");
			if (parser.equals("program")) {
				GlobalSettings.whatParser = GlobalSettings.ParserType.PROGRAM;
			} else if (parser.equals("ground")) {
				GlobalSettings.whatParser = GlobalSettings.ParserType.GROUND;
			} else if (parser.equals("rules")) {
				GlobalSettings.whatParser = GlobalSettings.ParserType.RULES;
			} else if (parser.equals("binary")) {
				GlobalSettings.whatParser = GlobalSettings.ParserType.BINARY;
			} else {
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "Invalid parser: " + parser, "", ""));
			}
		}

		String sort = context.startSymbolPgm;
		if (System.getenv("KRUN_SORT") != null) {
			sort = System.getenv("KRUN_SORT");
		}
		if (cmd.hasOption("sort")) {
			sort = cmd.getOptionValue("sort");
		}

		try {
			ASTNode out = ProgramLoader.processPgm(pgm, path, javaDef, sort, context, GlobalSettings.whatParser);
			String kast;
			if (prettyPrint) {
				KastFilter kastFilter = new KastFilter(indentationOptions, nextline, context);
				out.accept(kastFilter);
				kast = kastFilter.getResult();
			} else {
				MaudeFilter maudeFilter = new MaudeFilter(context);
				out.accept(maudeFilter);
				kast = maudeFilter.getResult();
			}
			System.out.println(kast);

			if (GlobalSettings.verbose) {
				sw.printIntermediate("Maudify Program");
				sw.printTotal("Total");
			}
			GlobalSettings.kem.print();
		} catch (TransformerException e) {
			e.report();
		}
	}
}
