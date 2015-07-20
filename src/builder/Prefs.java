package builder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Prefs {
    private static Prefs ourInstance = new Prefs();
    private static HashMap<String, Object> preferences;

    public static final String OUTPUT_DIR ="outputDir",
                                SECURE_OUTPUT = "outputSecure",
                                INSECURE_OUTPUT = "outputInsecure",
                                CRYPTO = "crypto",
                                SIGNATURE = "Signature",
                                TSA = "TSA",
                                SECURED_CLASSES = "secureClasses",
                                PROJECT_SOURCE = "Source",
                                CLASS_HOME = "ClassHome",
                                LIBRARIES = "Libs",
                                SIG_NAME = "SignedName";

    public static Prefs getInstance() {
        return ourInstance;
    }

    private Prefs() {
        preferences = new HashMap<>();
    }

    public void loadPrefs(String filePath) throws FileNotFoundException, MisformattedPreferences {
        File prefsFile = new File(filePath);
        Scanner reader = new Scanner(prefsFile);
        String line;
        boolean classFilter = false, libs = false;
        ArrayList<String> securedClasses = new ArrayList<>(), libraries = new ArrayList<>();
        do {
            line = reader.nextLine();
            String[] values = line.split(File.pathSeparator); // pathSeperator == ':'
            if (values[0].replace(" ", "").equals(SECURED_CLASSES)){
                classFilter = true;
                libs = false;
            }
            else if (values[0].replace(" ", "").equals(LIBRARIES)){
                classFilter = false;
                libs = true;
            }
            else if (values.length == 1 && classFilter){
                securedClasses.add(values[0].replace(" ", ""));
            }
            else if (values.length == 1 && libs){
                libraries.add(values[0].replace(" ", ""));
            }
            else if (values.length == 2){
                preferences.put(values[0].replace(" ", ""), values[1].replace(" ", ""));
            }
            else {
                throw new MisformattedPreferences();
            }
        } while(reader.hasNext());
        preferences.put(SECURED_CLASSES, securedClasses);
        preferences.put(LIBRARIES, libraries);
    }

    public Object getPrefrenece(String pref) throws NoSuchPreferenceException {
        if (preferences.containsKey(pref)) {
            return preferences.get(pref);
        }
        else {
            throw new NoSuchPreferenceException();
        }
    }

    public static class MisformattedPreferences extends Exception{

    }

    public static class NoSuchPreferenceException extends Exception{

    }

    public static void main(String[] args) throws IOException {

        String a = "hello!";
        String b = a;
        a = "Something else";
        System.out.println("A, b: " + a + ", " + b);
    }

    public static <T extends Object> T getPreference(String pref) throws NoSuchPreferenceException {
        return (T) getInstance().getPrefrenece(pref);
    }
}
