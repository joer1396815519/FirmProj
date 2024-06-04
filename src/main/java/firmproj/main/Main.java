package firmproj.main;

import firmproj.base.RetrofitPoint;
import firmproj.graph.CallGraph;
import firmproj.utility.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;

import org.apache.commons.cli.CommandLine;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.util.Chain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static boolean outputBackwardContexts;
    public static boolean outputJimpleFiles;
    private static void iniLog(String app) {
        LOGGER.info("Start Analysing {}", app);
        LOGGER.debug("Start Analysing {}", app);
        LOGGER.error("Start Analysing {}", app);
        LOGGER.warn("Start Analysing {}", app);
    }
    private static void endLog() {
        LOGGER.info("---------------------");
        LOGGER.debug("---------------------");
        LOGGER.error("---------------------");
        LOGGER.warn("---------------------");
    }

    public static void main(String[] args) throws IOException{
        CommandLine cmd = CommandLineOptions.parseOptions(CommandLineOptions.getNewOptions(), args);
        if (cmd == null || cmd.getOptionValue(CommandLineOptions.platform) == null ||
                //cmd.getOptionValue(CommandLineOptions.desc) == null ||
                (cmd.getOptionValue(CommandLineOptions.apk) == null && cmd.getOptionValue(CommandLineOptions.listOfApk) == null)) {
            LOGGER.error("cmd error");
            System.exit(1);

        }
        String outputPath = Config.RESULTDIR;

        if (cmd.hasOption(CommandLineOptions.outputPath)) {
            outputPath = cmd.getOptionValue(CommandLineOptions.outputPath);
        }

        //if (cmd.hasOption(CommandLineOptions.taintRules)) {
        //    BackwardTracing.createInstance(cmd.getOptionValue(CommandLineOptions.taintRules));
        //}

        if (cmd.hasOption(CommandLineOptions.outputGraph)) {
            Config.OUTPUT_GRAPHS = true;
        }
        if (cmd.hasOption(CommandLineOptions.outputGraphPath)) {
            Config.GRAPH_RESULT_DIR = cmd.getOptionValue(CommandLineOptions.outputGraphPath);
        }
        if (cmd.hasOption(CommandLineOptions.maxBackwardSteps)) {
            Config.MAXBACKWARDSTEPS = Integer.parseInt(cmd.getOptionValue(CommandLineOptions.maxBackwardSteps));
        }
        if (cmd.hasOption(CommandLineOptions.maxBackwardContext)) {
            Config.MAXBACKWARDSTEPS = Integer.parseInt(cmd.getOptionValue(CommandLineOptions.maxBackwardContext));
        }

        if (cmd.hasOption(CommandLineOptions.timeoutBackward)) {
            Config.TIMEOUT_BACKWARDS = Integer.parseInt(cmd.getOptionValue(CommandLineOptions.timeoutBackward)) * 60;
        }
        if (cmd.hasOption(CommandLineOptions.timeoutForward)) {
            Config.TIMEOUT_FORWARD = Integer.parseInt(cmd.getOptionValue(CommandLineOptions.timeoutForward)) * 60;
        }
        outputBackwardContexts = cmd.hasOption(CommandLineOptions.outputBackwardContexts);
        outputJimpleFiles = cmd.hasOption(CommandLineOptions.outputJimpleFiles);
        initDirs(outputPath);

        // set Android jar, which is later needed from soot
        Config.ANDROID_JAR_DIR = cmd.getOptionValue(CommandLineOptions.platform);
        String jarToLoad;
        if (Config.ANDROID_JAR_DIR.endsWith(".jar")) {
            jarToLoad = Config.ANDROID_JAR_DIR;
        } else {
            List<Path> paths = new ArrayList<>();
            Files.list(new File(Config.ANDROID_JAR_DIR).toPath()).forEach(paths::add);
            int max = 0;
            String currentPath = "";
            for (Path path : paths) {
                try{
                    int current = Integer.parseInt(path.getFileName().toString().replace("android-", ""));
                    if (current > max) {
                        max =current;
                        currentPath = path.toString() + "/android.jar";
                    }
                }catch (Exception e) {

                }
            }
            jarToLoad = currentPath;

        }

        //parse description, containing methods to trace

        //JSONObject targetMethods = new JSONObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue(CommandLineOptions.desc)))));
        JSONObject targetMethods = new JSONObject();
        //set apk to analyse
        List<String> apksToAnalyse = new LinkedList<>();
        if (cmd.getOptionValue(CommandLineOptions.apk) != null) {
            apksToAnalyse.add(cmd.getOptionValue(CommandLineOptions.apk));
        } else {
            apksToAnalyse.addAll(getApksFromFile(cmd.getOptionValue(CommandLineOptions.listOfApk)));
        }

        // load exclusion list
        List<String> exclusionList = new ArrayList<>();
        if (cmd.getOptionValue(CommandLineOptions.exclusion) != null) {
            LOGGER.info("loading exclusion list");
            JSONObject exclusionJSON = new JSONObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue(CommandLineOptions.exclusion)))));
            JSONArray jsonArray = exclusionJSON.getJSONArray("exclude");
            for (int i = 0; i < jsonArray.length(); i++) {
                LOGGER.info("excluded: {}", jsonArray.getString(i));
                exclusionList.add(jsonArray.getString(i));
            }
        }

        JarUtility.apkToJar(cmd.getOptionValue(CommandLineOptions.dex2jar), cmd.getOptionValue(CommandLineOptions.apk));
        //ReflectionHelper.init(cmd.getOptionValue(CommandLineOptions.apk), jarToLoad);

        for (String apk : apksToAnalyse) {
            analyzeApk(apk, exclusionList, targetMethods, outputPath, cmd);
        }
        System.exit(0);
    }
    public static void configureSoot(ApkContext apkContext, List<String> exclusionList) {
        soot.G.reset();

        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(apkContext.getAbsolutePath()));

        if (Config.ANDROID_JAR_DIR.endsWith(".jar")) {
            Options.v().set_force_android_jar(Config.ANDROID_JAR_DIR);
        } else {
            Options.v().set_android_jars(Config.ANDROID_JAR_DIR);

        }

        Options.v().set_process_multiple_dex(true);

        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        //switch to output jimple
        Options.v().set_output_dir("jimple/");

        if (outputJimpleFiles) {
            Options.v().set_output_format(Options.output_format_jimple);
        } else {
            Options.v().set_output_format(Options.output_format_none);
        }
        Options.v().set_keep_line_number(true);
        //Options.v().set_keep_offset(true);
        Options.v().ignore_resolution_errors();


        //changes for exclude packages
        Options.v().set_exclude(exclusionList);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_ignore_resolution_errors(true);

        //soot.Main.v().autoSetOptions();

    }

    private static Thread initTool(String apk, List<String> exclusionList, boolean initSoot, String outputPath, CommandLine cmd) throws IOException {
        LOGGER.info("Setting up soot");
        ApkContext apkContext = ApkContext.getInstance(apk);
        if (cmd.hasOption(CommandLineOptions.dontOverwriteResult) && getOutputFile(outputPath).exists()) {
            System.exit(0);
        }
        if (initSoot) {
            configureSoot(apkContext, exclusionList);
        }

        LOGGER.info("Loading soot classes");
        try {
            Scene.v().loadNecessaryClasses();
        }catch (Throwable e) {
            LOGGER.error("Soot could not load classes...");
        }
        TimeWatcher timeWatcher = TimeWatcher.getTimeWatcher();
        Thread t = new Thread(timeWatcher);
        t.setDaemon(true);
        t.start();

        LOGGER.info("initialisation of the call graph");
        CallGraph.init();
        return t;
    }

    public static void analyzeApk(String apk, List<String> exclusionList, JSONObject targetMethods, String outputPath, CommandLine cmd) throws IOException {
        //URL.setURLStreamHandlerFactory(new CustomURLStreamHandlerFactory());
        long startTime = System.currentTimeMillis();

        iniLog(apk);
        Thread t = initTool(apk, exclusionList, true, outputPath, cmd);
        //Soot configuration and call graph initialisation
        long initTime = System.currentTimeMillis();
        List<RetrofitPoint> allMethod = GetFirmRelatedMethod(GetAllRetrofitAnnotationMethod());
        LOGGER.info("GetAllfirmMethod: " + allMethod.size());
        //List<ValuePoint> allValuePoints = getAllSolvedValuePoints(targetMethods, t, apk);
        long endTime = System.currentTimeMillis();
        TimeWatcher timeWatcher = TimeWatcher.getTimeWatcher();
        writeRetrofitOut(allMethod, outputPath);
        //writeOutput(timeWatcher, startTime, initTime, endTime, apk, allValuePoints, outputPath);

        if (Options.v().output_format() == 1) {
            PackManager.v().writeOutput();
        }

        endLog();
        //Trigger GC because old measurement data is not needed anymore
        System.gc();
        soot.G.reset();
    }

    public static HashSet<SootMethod> GetFirmRelatedMethod(HashSet<SootMethod> methods){
        HashSet<SootMethod> result = new HashSet<SootMethod>();
        for (SootMethod method: methods) {
            if(FirmwareRelated.isFirmRelated(method.getName()))
                result.add(method);
        }
        return result;
    }

    public static List<RetrofitPoint> GetFirmRelatedMethod(List<RetrofitPoint> methods){
        List<RetrofitPoint> result = new ArrayList<RetrofitPoint>();
        for (RetrofitPoint method: methods) {
            if(FirmwareRelated.isFirmRelated(method.getMethod().getName()))
                result.add(method);
        }
        return result;
    }

    public static List<RetrofitPoint> GetAllRetrofitAnnotationMethod(){
        List<RetrofitPoint> result = new ArrayList<RetrofitPoint>();
        Chain<SootClass> classes = Scene.v().getClasses();
        for(SootClass clz: classes){
            if(!clz.isApplicationClass()) continue;
            List<SootMethod> methods = new ArrayList<>(clz.getMethods());
            for(SootMethod method: methods){
                boolean isRetrofitI = false;
                for(Tag tag: method.getTags()){
                    if(isRetrofitI) break;
                    if(tag instanceof VisibilityAnnotationTag){
                        VisibilityAnnotationTag annotationTag = (VisibilityAnnotationTag) tag;
                        for(AnnotationTag annotation: annotationTag.getAnnotations()){
                            String type = annotation.getType();
                            if(type.equals("Lretrofit2/http/GET;") || type.equals("Lretrofit2/http/POST;")) {
                                isRetrofitI = true;
                                break;
                            }
                            else{
                                LOGGER.warn("not match type:" + type + " method: " + method);
                            }
                        }
                    }
                }
                if(isRetrofitI) {
                    RetrofitPoint m = new RetrofitPoint(method);
                    result.add(m);
                    LOGGER.info("Match New Method: " + m.toString());
                }
            }
        }
        return result;
    }

    public static void writeRetrofitOut(List<RetrofitPoint> methods,String outputPath){
        File outputFile = getOutputFile(outputPath);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {

            for(RetrofitPoint Rp: methods){
                writer.write("RetrofitMethod: \n");
                writer.write(Rp.toString());
                LOGGER.info(Rp.toString());
                writer.write("===================\n");
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static File getOutputFile(String outputPath) {
        File outputFile = new File(outputPath);
        if (outputFile.isDirectory()) {
            outputPath += ApkContext.getInstance().getPackageName() + ".txt";
            outputFile= new File(outputPath);
            LOGGER.info("create:"+outputPath+"\nRealPath: "+outputFile.getAbsolutePath());
        }
        return outputFile;
    }
    private static List<String> getApksFromFile(String path) {
        List<String> result = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = "";
            while (line != null) {
                line = reader.readLine();
                result.add(line);
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
        return result;
    }
    private static void initDirs(String outputPath) {
        File tmp = new File(outputPath);
        if (!tmp.exists()) {
            LOGGER.info("creating tmp directory");
            if (!outputPath.endsWith(".json")) {
                tmp.mkdir();
            }
        }
        tmp = new File(Config.LOGDIR);
        if (!tmp.exists())
            tmp.mkdir();
    }

}
