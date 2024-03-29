# Overview

This project fetches likes from a twitter account, converts them to images and uploads to your google photo album. After it runs the first time
you should be able to select the Twitter Photo Album in the nest hub google photo slideshow config

# Setup

Go to google apis and enable the photo-api and create a new project. You'll need to request offline access so that the app run continuously without requiring you to constantly log into google.
Place the client id and secret credentials in src/main/resources/photo-api-credentials.json, with json format:

{
  "clientId" : "yourclientid,
  "secret" : "yoursecret"
}

Run the GoogleOauthFlow via Intellij. This will open a browser to sign-in with your google account and grant access to your
app to access your google photos. If successful, json oauth credentials will be display in the browser. For example:

{
  "access_token": "ya29.A0Af56SMAi7PYdI9ivBVR0099ZFVetf7F4HApo2JCHKMFr1o80fEHrd9LBa3ED8kOHIgOAvOrjziyuFCYUXZZzX14_1vC5w-lqwrCrcUBYrZ2xDfcWuoqcszbjc1VLo7bOQ7y_8uqpQFKt1W8L2MJaPxERH305",
  "expires_in": 3599,
  "refresh_token": "1//04KQuJfEq6e0xCgYIARcAGAQlNwF-L9Irea2S7WHKmJUGdSxkR6Ax_JLsf1wF9qzYgD6_9Lqsi8fwYXZAefiNTAO5sZgl4Jgoui4",
  "scope": "https://www.googleapis.com/auth/photoslibrary https://www.googleapis.com/auth/photoslibrary.sharing",
  "token_type": "Bearer"
}

We only need the refresh token from this response as the app will periodically obtain an access token. Copy the refresh to file src/main/resources/photo-api-refresh-token.json, with json format:
{
 "refreshToken" : "yourrefreshtoken"
}

Apply for a twitter developer account via https://developer.twitter.com/en/apply-for-access Go to keys and tokens in the developer portal and download you api key and secret.

Next we'll need to obtain a bearer token for use with the Twitter likes endpoint by sending a request with the api key and secret key:

Get API key from developer.twitter.com and issue request to get the oauth 2 bearer token
 curl -u 'api key:app secret key'   --data 'grant_type=client_credentials'   'https://api.twitter.com/oauth2/token'

Should return something like

{"token_type":"bearer","access_token":"AAAA......................................................."}

Place the json results of this request in file src/main/resources/twitter-bearer-token.json

Unlike the google token this token does not expire but may be revoked

Init the npm-puppeteer project. Should just require an npm init

The google photo frame feature may not show photos/tweets that its algorithm deems low quality, however there's is a way to disable this behavior.
Under google home app, go to settings for your device, select Photo Frame and under Personal photo curation select "Live Albums".
For more information see the "Photo Curation" section here: https://support.google.com/googlehome/answer/9136992?co=GENIE.Platform%3DAndroid&hl=en

Create a properties file src/main/resources/app.properties with the twitter handle that should be used to obtain likes, the max number of likes to fetch and the name of the Google photo album the app should create. For example:

# twitter screen name to fetch likes
twitter.handle=
# maximum number of likes to fetch
twitter.like.maxFetch=200
# creates an album with title if does not exist to populate with tweets
google.photos.album.title=Twitter Photo Frame

Run the PhotoFrameServer via your IDE or on the command-line with SBT. It should create retrieve likes, create photo images and upload to Google.

Open the google home app, go to settings for your device, select Photo Frame and mark the Twitter Photo Album to be included. It should start to display on your device shortly. It will look for new likes every 15 minutes and add them to your google album
