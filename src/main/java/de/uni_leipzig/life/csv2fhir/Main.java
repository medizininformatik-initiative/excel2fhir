package de.uni_leipzig.life.csv2fhir;

import java.io.File;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            File dir = new File(args[0]);
            if (dir.isDirectory()) {
                Csv2Fhir converter = new Csv2Fhir(dir);
                converter.convertFiles();
            } else {
                System.out.println("Provided dir is no directory! Abort!");
            }
        }
    }
}
