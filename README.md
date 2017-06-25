# ZxingScanner

[![](https://jitpack.io/v/YounthBin/ZxingScanner.svg)](https://jitpack.io/#YounthBin/ZxingScanner)

扫码功能

引入库
```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
    

```
```
compile 'com.github.YounthBin:ZxingScanner:1.0.0'
```
targetSdkVersion 22
```
```
targetSdkVersion 22
```

- 1、首先添加权限

```
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.FLASHLIGHT" />
```
- 2、继承 MFCaptureActivity
```
public class TestScannerActivity extends MFCaptureActivity {
    private static final String TAG = "TestScannerActivity";

    @Override
    public int setLayoutResID() {
        return R.layout.lib_scan_capture;
    }

    @Override
    public void onSetContentViewBefore() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
    }

    @Override
    public void ScanCodebyCamera(Result result, Bitmap barcode) {

    }
}
```
- 3、添加布局
```
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <include layout="@layout/scan_capture" />

    </RelativeLayout>

</FrameLayout>
```
