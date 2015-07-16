package builder;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.*;

import java.util.ArrayList;
import java.util.HashMap;

public class DependancyCollector extends EmptyVisitor {
    Class<?> definition;
    JavaClass classDef;
    ArrayList<String> dependancies;
    public DependancyCollector(Class<?> clazz) throws ClassNotFoundException {
        this.definition = clazz;
        this.classDef = Repository.lookupClass(this.definition);
        this.dependancies = new ArrayList<>();
    }

    public DependancyCollector(String name) throws ClassNotFoundException {
        this(Class.forName(name));
    }

    @Override
    public void visitConstantClass(ConstantClass obj) {
        super.visitConstantClass(obj);
        ConstantPool pool = this.classDef.getConstantPool();
        String dependencyString = obj.getBytes(pool);
        this.dependancies.add(dependencyString);
    }

    public ArrayList<String> getDependancies(){
        DescendingVisitor visitor = new DescendingVisitor(this.classDef, this);
        visitor.visit();

        return this.dependancies;
    }

    public static ArrayList<String> dependencies(String clazz) throws ClassNotFoundException {
        DependancyCollector collector = new DependancyCollector(clazz);
        return collector.getDependancies();
    }

    public static ArrayList<String> dependencies(Class<?> clazz) throws ClassNotFoundException {
        DependancyCollector collector = new DependancyCollector(clazz);
        return collector.getDependancies();
    }

    public static void main(String[] args) {
        //test main
        HashMap<String, String> seen = new HashMap<>();
        DependancyCollector collector = null;
        try {
            collector = new DependancyCollector(DependancyCollector.class);
        } catch (ClassNotFoundException e) {
        }
        ArrayList<String> dependencies = collector.getDependancies();
        for (int i = 0; i < dependencies.size(); ++i) {
            String baseItem = dependencies.get(i);
            if (baseItem.substring(0, 4).equals("java")){
                dependencies.remove(baseItem);
            }
            else {
                seen.put(baseItem, "");
            }
        }

        //sample dive
        for (int i = 0; i < dependencies.size(); ++i){
            String dependency = dependencies.get(i);
            System.out.println(dependency);
            if (!dependency.substring(0, 4).equals("java") && !seen.containsKey(dependency)){ //if it's not part of the java standard library
                ArrayList<String> additionalDependencies = null;
                try {
                    additionalDependencies = DependancyCollector.dependencies(dependency);
                } catch (ClassNotFoundException e) {
                    System.out.print("");
                }
                if (additionalDependencies != null)
                    dependencies.addAll(additionalDependencies);
            }
            seen.put(dependency, "");
        }
    }
}