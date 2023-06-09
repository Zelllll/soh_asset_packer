/**
 * Main.java
 * Main program code
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main
{
    private static String _inputPath, _outputPath;
    private static ArrayList<File> _sceneFiles, _audioFiles, _objectFiles, _textFiles, _xmlFiles;

    /**
     * Setup
     */
    public static void main(String[] args)
    {
        if (args.length < 2)
        {
            System.out.println("usage: input_dir output_dir\n" +
                    "\tinput_dir: input directory containing rom hack files\n" +
                    "\toutput_dir: output directory");
        }
        else
        {
            // set paths from the user
            _inputPath = args[0];
            _outputPath = args[1];

            // check if the input path is valid, if not throw an exception
            File f = new File(_inputPath);
            if (!(f.exists() && f.isDirectory()))
            {
                throw new IllegalArgumentException("Input directory does not exist!!!");
            }

            System.out.println("Input directory exists...");

            // check if the output path is valid, and if not attempt to create it
            f = new File(_outputPath);
            if (!(f.exists() && f.isDirectory()))
            {
                f.mkdir();
                System.out.println("Output directory created...");
            }

            System.out.println("Output directory exists...");

            // create an array of all the input files
            File[] files = new File(_inputPath).listFiles();

            _sceneFiles = new ArrayList<>();
            _audioFiles = new ArrayList<>();
            _objectFiles = new ArrayList<>();
            _textFiles = new ArrayList<>();
            _xmlFiles = new ArrayList<>();

            // split the array of all files into individual file type arrays
            splitFileTypes(files);

            // generate output
            build();

            System.out.println("Success!");
        }
    }

    public static void splitFileTypes(File[] files)
    {
        for (File f : files)
        {
            String fileName = f.getName();

            // if the file is a directory, continue
            if (!f.isFile())
            {
                continue;
            }

            if (fileName.endsWith(".xml"))
            {
                // check if file is a xml
                _xmlFiles.add(f);
            }
            else if (fileName.endsWith("_scene") || fileName.contains("_room_"))
            {
                // check if it is a scene/room file
                _sceneFiles.add(f);
            }
            else if (fileName.startsWith("object_"))
            {
                // check if it is an object file
                _objectFiles.add(f);
            }
            else
            {
                // check if it is an audio file
                for (String s : Globals.AUDIO_FILE_NAMES)
                {
                    if (fileName.equals(s))
                    {
                        _audioFiles.add(f);
                    }
                }

                // check if it is a text file
                for (String s : Globals.TEXT_FILE_NAMES)
                {
                    if (fileName.equals(s))
                    {
                        _textFiles.add(f);
                    }
                }
            }
        }
    }

    /**
     * Output ROM building
     */
    private static void build()
    {
        RomWriter rom = new RomWriter();
        Z64Code code = new Z64Code();

        // build each section of the rom
        buildScenes(rom);
        buildObjects(rom);
        buildText(rom, code);
        buildAudio(rom, code);
        buildCode(rom, code);

        // print random meme string
        System.out.println(Globals.MEME_STRINGS[(new Random()).nextInt(Globals.MEME_STRINGS.length)]);

        // save rom to disk
        rom.saveRom(_outputPath);
    }

    /**
     * Object generation
     */
    // builds the object section of the ROM
    private static void buildObjects(RomWriter rom)
    {
        System.out.println("Building objects...");

        // if there are no object files, do not attempt to instantiate a Z64Object object
        if (_objectFiles.size() == 0)
        {
            return;
        }

        for (File f : _objectFiles)
        {
            Z64Object newObject = new Z64Object(f);

            for (RomFile rf : newObject)
            {
                rom.add(rf);
            }
        }
    }

    /**
     * Audio generation
     */
    // builds the audio section of the ROM
    private static void buildAudio(RomWriter rom, Z64Code code)
    {
        System.out.println("Building audio...");

        // if there are no audio files, do not attempt to instantiate a Z64Audio object
        if (_audioFiles.size() == 0)
        {
            return;
        }

        // instantiate a Z64Audio object and build
        Z64Audio audio = new Z64Audio(_audioFiles, code);

        for (RomFile rf : audio)
        {
            rom.add(rf);
        }
    }

    /**
     * Text generation
     */
    private static void buildText(RomWriter rom, Z64Code code)
    {
        System.out.println("Building text...");

        // if there are no text files, do not attempt to instantiate a Z64Audio object
        if (_textFiles.size() == 0)
        {
            return;
        }

        // instantiate a Z64Text object and build
        Z64Text text = new Z64Text(_textFiles, code);

        for (RomFile rf : text)
        {
            rom.add(rf);
        }
    }

    /**
     * Code generation
     */
    // builds the code section of the ROM
    // note that this should be the last step in the build process
    private static void buildCode(RomWriter rom, Z64Code code)
    {
        for (RomFile romFile : code)
        {
            rom.add(romFile);
        }

        code.writeDataOffsets(_outputPath);
    }

    /**
     * Scene generation
     */
    // builds the scene section of the ROM
    private static void buildScenes(RomWriter rom)
    {
        System.out.println("Building scenes...");

        ArrayList<Z64Scene> sceneList = genSceneList();

        // add all the scenes and rooms to the rom
        for (Z64Scene scene : sceneList)
        {
            // add file to the rom
            for (RomFile romFile : scene)
            {
                rom.add(romFile);
            }

            // generate xml
            scene.saveXml(_outputPath);
        }
    }

    // generate the scene list
    private static ArrayList<Z64Scene> genSceneList()
    {
        ArrayList<Z64Scene> out = new ArrayList<>();

        for (File f : _sceneFiles)
        {
            // check if the file is a scene
            if (f.getName().endsWith("_scene"))
            {
                // create a new scene object
                Z64Scene scene = new Z64Scene(new RomFile(f));

                // add all rooms
                addRoomsToScene(scene);

                // add scene object to the output list
                out.add(scene);
            }
        }

        return out;
    }

    // gets the index contained in a room file name
    // so (getIndexFromRoomName("jabu_jabu_room_2") == 2), (getIndexFromRoomName("jabu_jabu_room_25") == 25), etc.
    private static int getIndexFromRoomName(String roomName)
    {
        // sourced from StackOverflow
        // https://stackoverflow.com/questions/3083154/how-can-i-get-the-last-integer-56-from-string-like-ra12ke43sh56

        Pattern p = Pattern.compile("[0-9]+$");
        Matcher m = p.matcher(roomName);
        if (m.find())
        {
            return Integer.parseInt(m.group());
        }
        else
        {
            throw new RuntimeException();
        }
    }

    // adds all of a scene's rooms
    private static void addRoomsToScene(Z64Scene scene)
    {
        String sceneName = scene.getName();
        ArrayList<File> roomInputFiles = new ArrayList<>();
        int lastRoomAdded = -1;

        // add all the room file names to the list
        for (File f : _sceneFiles)
        {
            String fileName = f.getName();

            if (fileName.startsWith(sceneName + "_room_"))
            {
                roomInputFiles.add(f);
            }
        }

        while (lastRoomAdded < roomInputFiles.size() - 1)
        {
            for (File f : roomInputFiles)
            {
                int roomIndex = getIndexFromRoomName(f.getName());
                if (roomIndex == lastRoomAdded + 1)
                {
                    scene.addRoom(new RomFile(f));
                    lastRoomAdded++;
                }
            }
        }
    }
}
