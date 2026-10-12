DH -> Xaero Bridge  (Minecraft 1.21.1, Fabric, singleplayer)
=============================================================

WHAT IT DOES
  Shows Distant Horizons LODs on Xaero's World Map where no real chunk data exists.
  Real (naturally generated / explored) map data is never replaced or touched.
  It only READS from Distant Horizons. It never writes into your world folder.

NEEDS (in your mods folder)
  Fabric Loader, Xaero's World Map 1.45.0, Distant Horizons 3.2.0-b (1.21.1)

HOW TO BUILD THE JAR (no coding)  -- see the step list in the chat message, short version:
  1. Make a free account on github.com
  2. New repository -> name it dh-xaero-bridge -> choose PRIVATE -> Create
  3. "uploading an existing file" -> drag EVERYTHING from this folder in -> Commit changes
  4. Open the "Actions" tab. Wait for the green check (3-6 minutes).
  5. Click the finished run -> scroll to "Artifacts" -> download dh-xaero-bridge-mod -> unzip -> the .jar inside is the mod.
  6. Put the .jar into .minecraft/mods

IF THE .github FOLDER DID NOT UPLOAD (Actions tab shows nothing):
  Add file -> Create new file -> type  .github/workflows/build.yml  as the name,
  paste the contents of the build.yml file from this folder, Commit.

IF YOU PREFER BUILDING ON YOUR OWN PC
  Install Java 21 (Adoptium Temurin), open a terminal in this folder, run:
      Windows:  gradlew.bat build
      Mac/Linux: ./gradlew build
  The jar appears in build/libs/

IN-GAME
  - Open your world, open the Xaero world map. Areas with LODs but no real chunks now appear.
  - Areas you already viewed earlier were saved by Xaero without LODs. Refresh them:
    World Map settings -> "Reload Viewed Regions"  (fast, only what is on screen)
    ("Reload All Regions" does every region incl. every LOD region - can take a long time.)
  - Log line to look for in latest.log:  "DH -> Xaero Bridge loaded"

WHAT'S NEW IN 1.0.3
- The thin black 1-chunk border around areas you explored yourself is now filled in
  (it borrows the look of the nearest Distant Horizons chunk).
- When Distant Horizons generates more terrain while you play, regions already on your
  map are redrawn automatically the next time they are on screen (no more need to press
  "Reload All Regions" just to see new LODs).
