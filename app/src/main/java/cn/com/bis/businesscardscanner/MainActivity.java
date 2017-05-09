package cn.com.bis.businesscardscanner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.Line;
import com.microsoft.projectoxford.vision.contract.OCR;
import com.microsoft.projectoxford.vision.contract.Region;
import com.microsoft.projectoxford.vision.contract.Word;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_TAKE_PHOTO = 0;
    private static final int REQUEST_SELECT_IMAGE_IN_ALBUM = 1;

    private TextView name, address, company, tel;

    // The URI of photo taken from gallery
    private Uri mUriPhotoTaken;

    // File of the photo taken with camera
    private File mFilePhotoTaken;

    // The URI of the image selected to detect.
    private Uri mImageUri;

    // The image selected to detect.
    private Bitmap mBitmap;

    private VisionServiceClient client;
    private static final int REQUEST_SELECT_IMAGE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Intent intent;
//                intent = new Intent(MainActivity.this, SelectImageActivity.class);
//                startActivityForResult(intent, REQUEST_SELECT_IMAGE);
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if(intent.resolveActivity(getPackageManager()) != null) {
                    // Save the photo taken to a temporary file.
                    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    try {
                        mFilePhotoTaken = File.createTempFile(
                                "IMG_",  /* prefix */
                                ".jpg",         /* suffix */
                                storageDir      /* directory */
                        );

                        // Create the File where the photo should go
                        // Continue only if the File was successfully created
                        if (mFilePhotoTaken != null) {
                            mUriPhotoTaken = FileProvider.getUriForFile(MainActivity.this,
                                    "com.microsoft.projectoxford.visionsample.fileprovider",
                                    mFilePhotoTaken);
                            intent.putExtra(MediaStore.EXTRA_OUTPUT, mUriPhotoTaken);

                            // Finally start camera activity
                            startActivityForResult(intent, REQUEST_TAKE_PHOTO);
                        }
                    } catch (IOException e) {
//                        setInfo(e.getMessage());
                    }
                }
            }
        });

        if (client==null){
            client = new VisionServiceRestClient(getString(R.string.subscription_key));
        }

        name = (TextView) findViewById(R.id.name);
        company = (TextView) findViewById(R.id.company);
        address = (TextView) findViewById(R.id.address);
        tel = (TextView) findViewById(R.id.tel);

    }

    // Recover the saved state when the activity is recreated.
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mUriPhotoTaken = savedInstanceState.getParcelable("ImageUri");
    }

    // Save the activity state when it's going to stop.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("ImageUri", mUriPhotoTaken);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_TAKE_PHOTO:
                if (resultCode == RESULT_OK) {
                    mImageUri = Uri.fromFile(mFilePhotoTaken);
                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());

                    // Add detection log.
                    Log.d("AnalyzeActivity", "Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                            + "x" + mBitmap.getHeight());

                    doAnalyze();
                }
                break;
            case REQUEST_SELECT_IMAGE_IN_ALBUM:
                if (resultCode == RESULT_OK) {
                    Uri imageUri;
                    if (data == null || data.getData() == null) {
                        imageUri = mUriPhotoTaken;
                    } else {
                        imageUri = data.getData();
                    }
                    Intent intent = new Intent();
                    intent.setData(imageUri);
                    setResult(RESULT_OK, intent);
                    finish();
                }

                break;
            default:
                break;
        }
    }

    public void doAnalyze() {

        try {
            new doRequest().execute();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    private String process() throws VisionServiceException, IOException {
        if (client==null){
            client = new VisionServiceRestClient(getString(R.string.subscription_key));
        }

        Gson gson = new Gson();
        String language = "unk";
//        String language = "ja";
        String[] details = {};

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

//        OCR v = client.recognizeText("https://portalstoragewuprod.azureedge.net/vision/OpticalCharacterRecognition/2-1.jpg", language, true);
        OCR v = client.recognizeText(inputStream, language, true);

        String result = gson.toJson(v);
        Log.d("result", result);

        return result;
    }

    private class doRequest extends AsyncTask<String, String, String> {
        // Store error message
        private Exception e = null;

        public doRequest() {
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return process();
            } catch (Exception e) {
                this.e = e;    // Store error
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            Gson gson = new Gson();
            OCR result = gson.fromJson(data, OCR.class);
//            String sresult = gson.toJson(result);
//            Log.d("result", sresult);
            List<Region> regions = result.regions;
            ArrayList<OriginalData> arr = new ArrayList();

            String tmp;
            String boundingBox;
            String[] bouding;
            String text;
            int width;
            int height;
            boolean isAscii = true;

            int lastWidth = 0;
            int lastHeight = 0;
            OriginalData org;
            for(Region region : regions) {
                for(Line line : region.lines) {
                    org = new OriginalData();

                    lastWidth = 0;
                    lastHeight = 0;
                    for(Word word : line.words) {
                        boundingBox = word.boundingBox;
                        bouding = boundingBox.split(",");
                        width = Integer.parseInt(bouding[2]);
                        height = Integer.parseInt(bouding[3]);
                        text = word.text;
                        if(lastWidth > 0 && lastHeight > 0) {
                            if(text.length() > 1) {
                                for(int i=0;i<text.length();i++) {
                                    if(text.charAt(i)>0x7F) {
                                        isAscii = false;
                                        break;
                                    }
                                }
                            }
                            if(!isAscii) {
                                if(!CommonUtils.isNear(lastWidth, width)&&!CommonUtils.isNear(lastHeight, height)) {
                                    arr.add(org);
                                    org = new OriginalData();
                                }
                            }
                        }

                        org.addData(word.text, width, height);
                        lastWidth = width;
                        lastHeight = height;

                    }
                    arr.add(org);
                }
            }

//            Collections.sort(arr,new Comparator<OriginalData>(){
//                public int compare(OriginalData arg0, OriginalData arg1) {
//                    return arg0.getArea().compareTo(arg1.getArea());
//                }
//            });

            BusinessCard card = new BusinessCard();
            int idx = 0;
            boolean setName = false;
            String temp;
            boolean isContinue = true;
            Integer maxWidth = 0;
            OriginalData orgMax = new OriginalData();
            for(OriginalData org1 : arr) {
                if(org1.getWord().indexOf("株式会社") > -1 || org1.getWord().indexOf("有限公司") > -1 || org1.getWord().indexOf("公司") > -1) {
                    card.company = org1.getWord();
                    continue;
                }

                if(org1.getWord().indexOf("東京都") > -1 || org1.getWord().indexOf("北海道") > -1 || org1.getWord().indexOf("大阪府") > -1 || org1.getWord().indexOf("県") > -1) {
                    if(org1.getWord().length() > 6) {
                        int startIdx = org1.getWord().indexOf(0x3A);
                        if(startIdx < 0) {
                            startIdx = org1.getWord().indexOf('：');
                        }
                        card.address = org1.getWord().substring(startIdx+1);
                        continue;
                    }
                }

                if(org1.getWord().indexOf("省") > -1 || org1.getWord().indexOf("市") > -1 || org1.getWord().indexOf("县") > -1) {
                    if(org1.getWord().length() > 6) {
                        int startIdx = org1.getWord().indexOf(0x3A);
                        if(startIdx < 0) {
                            startIdx = org1.getWord().indexOf('：');
                        }
                        card.address = org1.getWord().substring(startIdx+1);
                        continue;
                    }
                }

                if(org1.getWord().toUpperCase().indexOf("TEL") > -1) {
                    int s = org1.getWord().toUpperCase().indexOf("TEL");
                    s=s+3;

                    temp = "";
                    for(int i = s; i<org1.getWord().length();i++) {
                        if(org1.getWord().charAt(i) == 0x3A) {
                            continue;
                        }
                        if(0x3F >= org1.getWord().charAt(i) && org1.getWord().charAt(i) >= 0x21) {
                            temp = temp + org1.getWord().charAt(i);
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '：') {
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '一' || org1.getWord().charAt(i) == '（' || org1.getWord().charAt(i) == '）') {
                            temp = temp + org1.getWord().charAt(i);
                            continue;
                        }
                        break;
                    }
                    card.tel = temp;
                    continue;
                }else if(org1.getWord().toUpperCase().indexOf("MOBILE") > -1) {
                    int s = org1.getWord().toUpperCase().indexOf("MOBILE");
                    s=s+6;

                    temp = "";
                    for(int i = s; i<org1.getWord().length();i++) {
                        if(org1.getWord().charAt(i) == 0x3A) {
                            continue;
                        }
                        if(0x3F >= org1.getWord().charAt(i) && org1.getWord().charAt(i) >= 0x21) {
                            temp = temp + org1.getWord().charAt(i);
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '：') {
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '一') {
                            temp = temp + "-";
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '（') {
                            temp = temp + "(";
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '）') {
                            temp = temp + ")";
                            continue;
                        }
                        break;
                    }
                    card.tel = temp;
                    continue;
                }else if(org1.getWord().toUpperCase().indexOf("电话") > -1) {
                    int s = org1.getWord().toUpperCase().indexOf("电话");
                    s=s+2;

                    temp = "";
                    for(int i = s; i<org1.getWord().length();i++) {
                        if(org1.getWord().charAt(i) == 0x3A) {
                            continue;
                        }
                        if(0x3F >= org1.getWord().charAt(i) && org1.getWord().charAt(i) >= 0x21) {
                            temp = temp + org1.getWord().charAt(i);
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '：') {
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '一') {
                            temp = temp + "-";
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '（') {
                            temp = temp + "(";
                            continue;
                        }
                        if(org1.getWord().charAt(i) == '）') {
                            temp = temp + ")";
                            continue;
                        }
                        break;
                    }
                    card.tel = temp;
                    continue;
                }


                boolean isCJK = true;
                if(maxWidth < org1.heights.get(0)) {
                    if(org1.getWord().length() < 2) {
                        continue;
                    }
                    if(org1.getWord().length() > 6) {
                        continue;
                    }
                    for(int i =0; i<org1.getWord().length();i++) {
                        if(0x3040 <= org1.getWord().charAt(i) && org1.getWord().charAt(i) <= 0x309F) {
                            continue;
                        }
                        if(0x30A0 <= org1.getWord().charAt(i) && org1.getWord().charAt(i) <= 0x30FF) {
                            continue;
                        }
                        if(0x31F0 <= org1.getWord().charAt(i) && org1.getWord().charAt(i) <= 0x31FF) {
                            continue;
                        }
                        if(0x3200 <= org1.getWord().charAt(i) && org1.getWord().charAt(i) <= 0x32FF) {
                            continue;
                        }
                        if(0x3400 <= org1.getWord().charAt(i) && org1.getWord().charAt(i) <= 0x4DBF) {
                            continue;
                        }
                        if(0x4E00 <= org1.getWord().charAt(i) && org1.getWord().charAt(i) <= 0x9FBF) {
                            continue;
                        }
                        if(0xF900 <= org1.getWord().charAt(i) && org1.getWord().charAt(i) <= 0xFAFF) {
                            continue;
                        }
                        isCJK = false;
                    }
                    if(isCJK) {
                        maxWidth = org1.heights.get(0);
                        orgMax = org1;
                    }
                }
                idx++;
            }

            name.setText("姓名：" + orgMax.getWord());
            company.setText("公司：" + card.company);
            address.setText("地址：" + card.address);
            tel.setText("电话：" + card.tel);
        }
    }
}
