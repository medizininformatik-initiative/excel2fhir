package heuschkel.life.de;

import heuschkel.life.de.Convertable.Person;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class csv2fhir {
    private File dir;
    private List<String> files2convert= Arrays.asList("Person.csv");

    public csv2fhir(File directory){
        this.dir = directory;

    }

    private Convertable2Fhir getClass(File file) throws Exception {
        if ("Person.csv".equals(file.getName())) {
            return new Person(file);
        }else {
            throw new Exception("no mathing Class to convert found!");
        }
    }
    public void convert() throws Exception {
        for (String file_string: files2convert) {
            File file = new File(dir.getPath() + "/" + file_string);
            File out = new File(dir.getPath() + "/"+file_string.substring(0,file_string.length()-4) + ".json");
            if(file.exists()){
                if(out.exists() && out.isFile()){
                    System.out.println("File '" + out.getPath() +"' already exists! Override? [y/n]");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String overide = reader.readLine();
                    if(!overide.equals("y") && !overide.equals("Y")){
                        System.out.println("File '" + out.getPath() +"' will NOT be overridden. ");
                        return;
                    }
                }
                FileWriter writer = new FileWriter(out);
                writer.write(getClass(file).convert());
                writer.flush();
                writer.close();
            }
        }

    }
}
