package heuschkel.life.de;

import java.io.File;
import java.io.IOException;

/**
 * Main
 *
 */
public class Main
{
    public static void main( String[] args ) throws Exception {
        System.out.println( "Hello World!" );
        if (args.length == 1) {
            File dir = new File(args[0]);
            if (dir.isDirectory()){
                csv2fhir converter = new csv2fhir(dir);
                converter.convert();
            }

        }
    }
}
