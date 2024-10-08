# Electric Objects Lives!  Replacement APK

![](e01.png "e01")

## Getting started

- This is a WIP and is not finished, *do not try and upload this to your device* it's not ready yet. I will be attempting to add Google Photos support instead of Flickr.

### Requirements 

- You need a way to connect a keyboard and mouse to your EO1.  I got one of these -- https://www.amazon.com/gp/product/B01C6032G0/ -- and connected my USB keyboard to it, then my USB mouse to the keyboard.
- Google Photos public URL: You need a public URL of a Google Photos album. For example, you can use this URL: https://photos.app.goo.gl/BCHX2d2fCXCjUEws9
- Upon setting up the app, it'll ask for the Google Photos public URL. You can either type it in on the setup dialog, or put it into a file named **"config.txt"** and copy it to your EO1's "Downloads" folder. (An easy way to do this is to email yourself the file then log into your email and download it using the EO1's web browser [described below]).

### Setup

- Upload some EO art to your Google Photos album. MP4 videos and still images are supported.
- Once you boot up your EO1 and it hangs on the "Getting Art" dialog, hit **WINDOWS + B** to open a web browser
- You need to tell your EO1 to allow side-loading.  Swipe down on the top right and go to Settings > Security.  In there make sure "Unknown Sources" is checked.
- Go back to the browser and go to this URL: https://github.com/shellen/EO1/releases/download/0.0.8/EO1.apk (you may need to use the mirror, [by clicking here](http://danexchtest11.cloudapp.net/private/releases/EO1.apk))
- When it finishes, install the file by pulling down the notification bar and clicking it, then agreeing to the prompts.
- Restart/power cycle your EO1
- Because this APK is designated as a "Home screen replacement", when it boots, it will ask if you want to load the Electric Object app, or the EO1 app.  Select EO1 and choose "Always".
- The first time the EO1 is run you will need to specify the information above.  Click OK to save and continue.  **To get back to the configuration screen later, push C on your connected keyboard** 
- You can now unplug your mouse and keyboard and hang your EO1 back on the wall!
- See "Partner App" below for instructions on installing the companion app on your Android device.

### New in 0.0.8

- Fixes custom tag ocassionally losing value (take 2)
- Fixes random dimming during static photo or video display
- Better error recovery during media download
- Adds retreiving additional media after #500 -- app will cycle through "pages" of responses from the Google Photos album 
- Adds video media caching and auto-cache cleanup: video art will be cached and not re-downloaded when possible

### New in 0.0.7
Device app:
- Fixes the custom tag and other settings ocassionally losing values, especially when coming back from quiet hours.

### New in 0.0.6

Device app:
- Fixes brightness being reset upon coming back from quiet hours
  
Partner app:
- No changes
 
### New in 0.0.5

Device app:
- Checkbox allows you to disable auto-brightness and set it manually

Partner app:
- Gear icon at top right lets you send new values for brightness, start/end quiet hour, and slideshow interval direct to the device (see 0.0.3 for instructions on installing the partner app)

### New in 0.0.4

Device app:
- Fixes quiet hours after midnight
- Fixes image and video pushing via partner app when original resolution is not available
- Better handling of bad/no network scenarios

Partner app:
- Handles intents from Google Photos app in a better way (you must upgrade the partner app if you update to 0.0.4 on the device)

### Partner App

- A "Partner App" (for Android) runs on your phone or mobile device and allows you to push images or video directly from the <A href="https://play.google.com/store/apps/details?id=com.google.android.apps.photos&hl=en_US&gl=US">Google Photos Android App</a> using the share icon, assuming you are running on the same network as your EO1 device.  Running the Partner App from the Start menu of your phone allows you to skip to the next item in the current slideshow or resume the slideshow after sharing an individual item.  You can also update the current Tag (original Tag will be restored next time the device restarts), adjust brightness and change your slideshow interval.
- To install, you must allow "Unknown Sources" on your device.
- Point your device web browser to https://github.com/shellen/EO1/releases/download/0.0.6/EO1-Partner.apk and follow the prompts to install.  You may need to agree to several warnings.

### New in 0.0.2

- Fixed: low resolution images
- New: Specify slideshow interval in minutes
- New: Specify showing your gallery, public gallery (items tagged "ElectricObjectsLives"), or specific tag
- New: auto-brightness based on light sensor reading
- New: quiet hours (screen will dim and not display images during this period)
- New: Top button will dim/un-dim screen
- New: Space will advance slideshow manually
- New: C opens Config screen
- More goodies coming soon :)

### Art/Contact

- I need more art!  Do you have any?  
- Questions?  danf879@gmail.com

### Creating an APK from this fork

To create an APK from this fork, follow these steps:

1. Clone the repository:
   ```
   git clone https://github.com/shellen/EO1.git
   cd EO1
   ```

2. Open the project in Android Studio.

3. Build the APK using Gradle:
   - In Android Studio, select `Build` from the top menu.
   - Click on `Build Bundle(s) / APK(s)`.
   - Select `Build APK(s)`.

4. The APK will be generated and you can find it in the `app/build/outputs/apk/release` directory.
