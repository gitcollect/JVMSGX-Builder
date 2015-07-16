package builder;

import reference.WhiteList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class CodeMonkey {
    //this class will handle constructing new dummy files from existing classes.
    Class<?> definition;
    int numMethods;
    boolean isWhiteListed;
    String outputFileName, outputFolder, workingDirectory;

    public CodeMonkey(Class<?> classDefinition){
        this.definition = classDefinition;
        this.numMethods = classDefinition.getMethods().length;
        this.isWhiteListed = classDefinition.isAnnotationPresent(WhiteList.class);
        String[] className = classDefinition.getCanonicalName().split("///");
        this.outputFolder = "";
        for (int i = 0; i < className.length - 1; ++i){
            this.outputFolder += className[i] + File.separator;
        }

        this.outputFileName = className[className.length-1];
        this.workingDirectory = System.getProperty("user.dir");
    }

    public void setOutput(String output, String name){
        this.outputFolder = output;
        this.outputFileName = name;
    }

    public String getMethodStub(Method method){
        int modifiers = method.getModifiers();
        if (Modifier.isFinal(modifiers) || !method.getDeclaringClass().equals(definition)){
            //conditions that will be covered by inheritance.
            return "";
        }
        String methodBody = "\t";

        methodBody += Modifier.toString(modifiers) + " ";

        methodBody += method.getReturnType().getCanonicalName() + " ";
        methodBody += method.getName() + "(";

        //generate parameters
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 1){

        }
        else if (parameterTypes.length > 1){
            methodBody += parameterTypes[0].getCanonicalName() + " arg0";
            for (int i = 1; i < parameterTypes.length; ++i){
                methodBody += ", ";
                Class<?> type = parameterTypes[i];
                methodBody += type.getCanonicalName() + " arg" + i;
            }
        }

        methodBody += ")";

        // add exceptions
        Class<?>[] exceptions = method.getExceptionTypes();
        if (exceptions.length > 0){
            methodBody += " throws " + exceptions[0].getCanonicalName();
            for (int i = 1; i < exceptions.length; ++i){
                Class<?> exception = exceptions[i];
                methodBody += ", " + exception.getCanonicalName();
            }
        }

        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)){
            methodBody += ";";
        }
        else {
            methodBody += " {";

            if (method.getReturnType() == void.class || method.getReturnType() == Void.class){
                methodBody += " }\n";
            }
            else {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class){
                    methodBody += " return false; }\n";
                }
                else if (returnType == int.class || returnType == float.class || returnType == double.class || returnType == long.class || returnType == short.class){
                    methodBody += " return 0; }\n";
                }
                else {
                    methodBody += " return null; }\n";
                }

            }
        }
        return methodBody;
    }

    public String getPropertyStub(Field field){
        String fieldString = "\t";
        fieldString += Modifier.toString(field.getModifiers()) + " ";
        fieldString += field.getType().getName() + " ";
        fieldString += field.getName();
        fieldString += ";\n";

        return fieldString;
    }

    public String getClassHeader(){
        //setup package if it exists
        String packageGroup = this.definition.getPackage().getName();
        String header = "package " + packageGroup + ";\n";
        header += Modifier.toString(this.definition.getModifiers()) + " class " + this.definition.getSimpleName() + " extends " + definition.getSuperclass().getCanonicalName();
        if (definition.getInterfaces().length > 0){
            Class<?>[] interfaces = definition.getInterfaces();
            header += " implements " + interfaces[0].getCanonicalName();
            for (int i = 1; i < interfaces.length; ++i){
                header += ", " + interfaces[i].getCanonicalName();
            }
        }
        header += " {\n";

        return header;
    }

    public String getClassFooter(){
        return "}";
    }

    public FileWriter openWriter(String path) throws IOException {
        FileWriter fos = new FileWriter(path);
        return fos;
    }

    public void createStub(){
        String path = this.outputFolder + File.separator;
        File outputFolder = new File(path);
        if (!outputFolder.exists()){
            outputFolder.mkdir();
        }

        try {
            FileWriter writer = openWriter(path + outputFileName);

            writer.write(getClassHeader());

            for (Field f : this.definition.getFields()){
                writer.write(getPropertyStub(f));
            }
            writer.write("\n");
            for (Method m : this.definition.getMethods()){
                writer.write(getMethodStub(m));
            }

            writer.write(getClassFooter());

            writer.flush();
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String createStubAsString(){
        String value = "";

        value += getClassHeader();

        for (Field f : this.definition.getFields()){
            value += getPropertyStub(f);
        }

        value += "\n";

        for (Method m : this.definition.getMethods()){
            value += getMethodStub(m);
        }

        value += getClassFooter();

        return value;
    }

    public static void main(String[] args){
        //this is a test main
        CodeMonkey constructor = new CodeMonkey(MySampleClass.class);
        String value = constructor.createStubAsString();

        System.out.println(value);
    }

    public static class MySampleClass {
        public Integer myInt = 10;
        public String myStr = "New Str";
        private MySampleClass sampleClass;

        public static void print(){
            System.out.println("NOTHING");
        }

        public void printNonStat(){
            System.out.println("EVEN MORE NOTHING");
        }

        public String getMyStr(){
            return myStr;
        }

        public MySampleClass mutateClass(){
            sampleClass = new MySampleClass();
            return sampleClass;
        }
    }
}