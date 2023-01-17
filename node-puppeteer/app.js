const puppeteer = require('puppeteer');
const { stringify } = require('querystring');

var path = require("path");

const inputDir = process.argv[2];
const aspectRatio = process.argv[3];

console.log("Looking for html files in " + inputDir)
console.log("Aspect ratio " + aspectRatio)
console.log("process.argv", process.argv);

const fs = require('fs');
const { basename } = require('path');

// TODO return json list of processing results
// TODO make into functions
fs.readdir(inputDir, (err, files) => {
  files.forEach(file => {
      if (path.extname(file) == ".html") {
        console.log('Processing ' + file);

        (async () => {
            const browser = await puppeteer.launch();
            const page = await browser.newPage();

            // load image and wait until js renders (images loaded)
            await page.goto("file://" + inputDir + "/" + file, {waitUntil: 'networkidle0'});

            // get actual dimensions of tweet
            const elem = await page.$('.twitter-tweet');
            const boundingBox = await elem.boundingBox();
            console.log('boundingBox', boundingBox)

            const heightPadding = 20;

            // TODO figure out nest hub aspect ration. It's cropping the width at 16:10. Whatever it is, it will crop however it sees fit for display
            const nestAspectRatio = Number.parseFloat(eval(aspectRatio))

            const minHeight = boundingBox.height + heightPadding; /* height is not reported correctly for some reason */
            const minWidth = boundingBox.width;

            const effectiveHeight = boundingBox.height + heightPadding

            console.log('min width ' + minWidth + ', min height ' + minHeight)

            const adjustHeight = Math.round( boundingBox.width / nestAspectRatio )
            const adjustWidth = Math.round(boundingBox.height * nestAspectRatio)

            if (adjustHeight > effectiveHeight) {
                console.log('setting viewport to ' + boundingBox.width + ', by ' + adjustHeight);
                await page.setViewport({ width: boundingBox.width, height: adjustHeight })
            } else {
                console.log('setting viewport to ' + adjustWidth + ', by ' + effectiveHeight);
                await page.setViewport({ width: adjustWidth, height: effectiveHeight })
            }

            await page.screenshot({path: path.basename(file, 'html') + 'jpg', type: 'jpeg'})
            await browser.close();
        })();
      } else {
          console.log('Ignoring non-html ' + file)
      }
  });
});