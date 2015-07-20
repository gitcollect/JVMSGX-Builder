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
        if (!method.getDeclaringClass().equals(definition)){
            //conditions that will be covered by inheritance.
            return "";
        }
        String methodBody = "\t" + getMethodHeader(method, modifiers);

        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)){
            methodBody += ";";
        }
        else {
            methodBody += getMethodBody(method);
        }

        return methodBody;
    }

    public String getMethodHeader(Method method, int modifiers){
        String methodBody = "";

        methodBody += Modifier.toString(modifiers) + " ";

        methodBody += method.getReturnType().getCanonicalName() + " ";
        methodBody += method.getName() + "(";

        Class<?>[] parameterTypes = method.getParameterTypes();

        if (parameterTypes.length > 0){
            methodBody += parameterTypes[0].getCanonicalName() + " arg0";
            for (int i = 1; i < parameterTypes.length; ++i){
                methodBody += ", ";
                Class<?> type = parameterTypes[i];
                methodBody += type.getCanonicalName() + " arg" + i;
            }
        }

        methodBody += ")";

        Class<?>[] exceptions = method.getExceptionTypes();
        if (exceptions.length > 0){
            methodBody += " throws " + exceptions[0].getCanonicalName();
            for (int i = 1; i < exceptions.length; ++i){
                Class<?> exception = exceptions[i];
                methodBody += ", " + exception.getCanonicalName();
            }
        }

        return methodBody;
    }

    public String getMethodBody(Method method){
        String methodBody = "";
        methodBody += " {";

        if (method.getReturnType() == void.class || method.getReturnType() == Void.class){
            methodBody += " }\n";
        }
        else {
            Class<?> returnType = method.getReturnType();
            methodBody += " return ";

            if (returnType == boolean.class){
                methodBody += "false\n";
            }
            else if (isPrimitiveNumber(returnType)){
                methodBody += "0";
            }
            else {
                methodBody += "null";
            }
            methodBody += "; }\n";
        }
        return methodBody;
    }

    public boolean isPrimitiveNumber(Class<?> clazz){
        return clazz == int.class || clazz == float.class || clazz == double.class || clazz == long.class || clazz == short.class;
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
        FileCollector.verifyDir(path);

        try {
            FileWriter writer = openWriter(path + outputFileName);

            String classData = createStubAsString();
            writer.write(classData);

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
        public MySampleClass doSomething(MySampleClass a, MySampleClass b){
            return new MySampleClass();
        }
        public final String getName(){
            return "My name";
        }

        public MySampleClass mutateClass(){
            sampleClass = new MySampleClass();
            return sampleClass;
        }
    }
}