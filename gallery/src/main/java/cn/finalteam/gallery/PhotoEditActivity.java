/*
 * Copyright (C) 2014 pengjianbo(pengjianbosoft@gmail.com), Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package cn.finalteam.gallery;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ez.EZGallery.R;
import cn.finalteam.gallery.adapter.PhotoEditListAdapter;
import cn.finalteam.gallery.model.PhotoInfo;
import cn.finalteam.gallery.model.PhotoTempModel;
import cn.finalteam.gallery.utils.RecycleViewBitmapUtils;
import cn.finalteam.gallery.utils.Utils;
import cn.finalteam.gallery.widget.FloatingActionButton;
import cn.finalteam.gallery.widget.HorizontalListView;
import cn.finalteam.gallery.widget.crop.CropImageActivity;
import cn.finalteam.gallery.widget.crop.CropImageView;
import cn.finalteam.gallery.widget.zoonview.PhotoView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.finalteam.toolsfinal.ActivityManager;
import cn.finalteam.toolsfinal.StringUtils;
import cn.finalteam.toolsfinal.io.FileUtils;
import cn.finalteam.toolsfinal.io.FilenameUtils;

/**
 * Desction:图片裁剪
 * Author:pengjianbo
 * Date:15/10/10 下午5:40
 */
public class PhotoEditActivity extends CropImageActivity implements AdapterView.OnItemClickListener, View.OnClickListener {

    static final String CROP_PHOTO_ACTION = "crop_photo_action";
    static final String TAKE_PHOTO_ACTION = "take_photo_action";
    static final String EDIT_PHOTO_ACTION = "edit_photo_action";

    static final String SELECT_MAP = "select_map";
    private final int CROP_SUC = 1;//裁剪成功
    private final int CROP_FAIL = 2;//裁剪失败
    private final int UPDATE_PATH = 3;//更新path

    private ImageView mIvBack;
    private TextView mTvTitle;
    private ImageView mIvTakePhoto;
    private ImageView mIvCrop;
    private ImageView mIvRotate;
    private ImageView mIvPreView;
    private CropImageView mIvCropPhoto;
    private PhotoView mIvSourcePhoto;
    private TextView mTvEmptyView;
    private FloatingActionButton mFabCrop;
    private HorizontalListView mLvGallery;
    private LinearLayout mLlGallery;
    private LinearLayout mTitlebar;

    private ArrayList<PhotoInfo> mPhotoList;
    private PhotoEditListAdapter mPhotoEditListAdapter;
    private int mSelectIndex = 0;
    private boolean mCropState;
    private ProgressDialog mProgressDialog;
    private boolean mRotating;

    private ArrayList<PhotoInfo> mSelectPhotoList;
    private LinkedHashMap<Integer, PhotoTempModel> mPhotoTempMap;
    private File mEditPhotoCacheFile;

    private Drawable mDefaultDrawable;


    private boolean mCropPhotoAction;//裁剪图片动作
    private boolean mEditPhotoAction;//编辑图片动作

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("selectPhotoMap", mSelectPhotoList);
        outState.putSerializable("editPhotoCacheFile", mEditPhotoCacheFile);
        outState.putSerializable("photoTempMap", mPhotoTempMap);

        outState.putInt("selectIndex", mSelectIndex);
        outState.putBoolean("cropState", mCropState);
        outState.putBoolean("rotating", mRotating);

        outState.putBoolean("takePhotoAction", mTakePhotoAction);
        outState.putBoolean("cropPhotoAction", mCropPhotoAction);
        outState.putBoolean("editPhotoAction", mEditPhotoAction);

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSelectPhotoList = (ArrayList<PhotoInfo>) getIntent().getSerializableExtra("selectPhotoMap");
        mEditPhotoCacheFile = (File) savedInstanceState.getSerializable("editPhotoCacheFile");
        mPhotoTempMap = new LinkedHashMap<>((HashMap<Integer, PhotoTempModel>) getIntent().getSerializableExtra("photoTempMap"));

        mSelectIndex = savedInstanceState.getInt("selectIndex");
        mCropState = savedInstanceState.getBoolean("cropState");
        mRotating = savedInstanceState.getBoolean("rotating");

        mTakePhotoAction = savedInstanceState.getBoolean("takePhotoAction");
        mCropPhotoAction = savedInstanceState.getBoolean("cropPhotoAction");
        mEditPhotoAction = savedInstanceState.getBoolean("editPhotoAction");
    }

    private android.os.Handler mHanlder = new android.os.Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if ( msg.what == CROP_SUC ) {
                String path = (String) msg.obj;
                PhotoInfo photoInfo = mPhotoList.get(mSelectIndex);
                try {
                    Iterator<Map.Entry<Integer, PhotoTempModel>> entries = mPhotoTempMap.entrySet().iterator();
                    while (entries.hasNext()) {
                        Map.Entry<Integer, PhotoTempModel> entry = entries.next();
                        if (entry.getKey() == photoInfo.getPhotoId()) {
                            PhotoTempModel tempModel = entry.getValue();
                            tempModel.setSourcePath(path);
                            tempModel.setOrientation(0);
                        }
                    }
                } catch (Exception e) {
                }
                toast(getString(R.string.crop_suc));

                Message message = mHanlder.obtainMessage();
                message.what = UPDATE_PATH;
                message.obj = path;
                mHanlder.sendMessage(message);

            } else if ( msg.what == CROP_FAIL ) {
                toast(getString(R.string.crop_fail));
            } else if ( msg.what == UPDATE_PATH ) {
                if (mPhotoList.get(mSelectIndex) != null) {
                    PhotoInfo photoInfo = mPhotoList.get(mSelectIndex);
                    String path = (String) msg.obj;
                    //photoInfo.setThumbPath(path);
                    try {
                        for(Iterator<PhotoInfo> iterator = mSelectPhotoList.iterator();iterator.hasNext();){
                            PhotoInfo info = iterator.next();
                            if (info != null && info.getPhotoId() == photoInfo.getPhotoId()) {
                                info.setPhotoPath(path);
                            }
                        }
                    } catch (Exception e) {
                    }
                    photoInfo.setPhotoPath(path);

                    loadImage(photoInfo);
                    mPhotoEditListAdapter.notifyDataSetChanged();
                }

                if (com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCrop() && !com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCropEdit()) {
                    resultAction();
                }
            }
            corpPageState(false);
            mCropState = false;
            mTvTitle.setText(R.string.photo_edit);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ( com.ez.gallery.GalleryFinal.getFunctionConfig() == null || com.ez.gallery.GalleryFinal.getGalleryTheme() == null) {
            resultFailureDelayed(getString(R.string.please_reopen_gf), true);
        } else {
            setContentView(R.layout.gf_activity_photo_edit);
            mDefaultDrawable = getResources().getDrawable(R.drawable.ic_gf_default_photo);

            mSelectPhotoList = (ArrayList<PhotoInfo>) getIntent().getSerializableExtra(SELECT_MAP);
            mTakePhotoAction = this.getIntent().getBooleanExtra(TAKE_PHOTO_ACTION, false);
            mCropPhotoAction = this.getIntent().getBooleanExtra(CROP_PHOTO_ACTION, false);
            mEditPhotoAction = this.getIntent().getBooleanExtra(EDIT_PHOTO_ACTION, false);

            if (mSelectPhotoList == null) {
                mSelectPhotoList = new ArrayList<>();
            }
            mPhotoTempMap = new LinkedHashMap<>();
            mPhotoList = new ArrayList<>(mSelectPhotoList);

            mEditPhotoCacheFile = com.ez.gallery.GalleryFinal.getCoreConfig().getEditPhotoCacheFolder();

            if (mPhotoList == null) {
                mPhotoList = new ArrayList<>();
            }

            for (PhotoInfo info : mPhotoList) {
                mPhotoTempMap.put(info.getPhotoId(), new PhotoTempModel(info.getPhotoPath()));
            }

            findViews();
            setListener();
            setTheme();

            mPhotoEditListAdapter = new PhotoEditListAdapter(this, mPhotoList, mScreenWidth);
            mLvGallery.setAdapter(mPhotoEditListAdapter);

            try {
                File nomediaFile = new File(mEditPhotoCacheFile, ".nomedia");
                if (!nomediaFile.exists()) {
                    nomediaFile.createNewFile();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCamera()) {
                mIvTakePhoto.setVisibility(View.VISIBLE);
            }

            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCrop()) {
                mIvCrop.setVisibility(View.VISIBLE);
            }

            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isRotate()) {
                mIvRotate.setVisibility(View.VISIBLE);
            }

            if (!com.ez.gallery.GalleryFinal.getFunctionConfig().isMutiSelect()) {
                mLlGallery.setVisibility(View.GONE);
            }

            initCrop(mIvCropPhoto, com.ez.gallery.GalleryFinal.getFunctionConfig().isCropSquare(), com.ez.gallery.GalleryFinal.getFunctionConfig().getCropWidth(), com.ez.gallery.GalleryFinal.getFunctionConfig().getCropHeight());
            if (mPhotoList.size() > 0 && !mTakePhotoAction) {
                loadImage(mPhotoList.get(0));
            }

            if (mTakePhotoAction) {
                //打开相机
                takePhotoAction();
            }
            if (mCropPhotoAction) {
                mIvCrop.performClick();
                if ( !com.ez.gallery.GalleryFinal.getFunctionConfig().isRotate() && !com.ez.gallery.GalleryFinal.getFunctionConfig().isCamera()) {
                    mIvCrop.setVisibility(View.GONE);
                }
            } else {
                //判断是否强制裁剪
                hasForceCrop();
            }

            if(com.ez.gallery.GalleryFinal.getFunctionConfig().isEnablePreview()){
                mIvPreView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setTheme() {
        mIvBack.setImageResource(com.ez.gallery.GalleryFinal.getGalleryTheme().getIconBack());
        if (com.ez.gallery.GalleryFinal.getGalleryTheme().getIconBack() == R.drawable.ic_gf_back) {
            mIvBack.setColorFilter(com.ez.gallery.GalleryFinal.getGalleryTheme().getTitleBarIconColor());
        }

        mIvTakePhoto.setImageResource(com.ez.gallery.GalleryFinal.getGalleryTheme().getIconCamera());
        if (com.ez.gallery.GalleryFinal.getGalleryTheme().getIconCamera() == R.drawable.ic_gf_camera) {
            mIvTakePhoto.setColorFilter(com.ez.gallery.GalleryFinal.getGalleryTheme().getTitleBarIconColor());
        }

        mIvCrop.setImageResource(com.ez.gallery.GalleryFinal.getGalleryTheme().getIconCrop());
        if (com.ez.gallery.GalleryFinal.getGalleryTheme().getIconCrop() == R.drawable.ic_gf_crop) {
            mIvCrop.setColorFilter(com.ez.gallery.GalleryFinal.getGalleryTheme().getTitleBarIconColor());
        }

        mIvPreView.setImageResource(com.ez.gallery.GalleryFinal.getGalleryTheme().getIconPreview());
        if (com.ez.gallery.GalleryFinal.getGalleryTheme().getIconPreview() == R.drawable.ic_gf_preview) {
            mIvPreView.setColorFilter(com.ez.gallery.GalleryFinal.getGalleryTheme().getTitleBarIconColor());
        }

        mIvRotate.setImageResource(com.ez.gallery.GalleryFinal.getGalleryTheme().getIconRotate());
        if (com.ez.gallery.GalleryFinal.getGalleryTheme().getIconRotate() == R.drawable.ic_gf_rotate) {
            mIvRotate.setColorFilter(com.ez.gallery.GalleryFinal.getGalleryTheme().getTitleBarIconColor());
        }

        if ( com.ez.gallery.GalleryFinal.getGalleryTheme().getEditPhotoBgTexture() != null ) {
            mIvSourcePhoto.setBackgroundDrawable(com.ez.gallery.GalleryFinal.getGalleryTheme().getEditPhotoBgTexture());
            mIvCropPhoto.setBackgroundDrawable(com.ez.gallery.GalleryFinal.getGalleryTheme().getEditPhotoBgTexture());
        }

        mFabCrop.setIcon(com.ez.gallery.GalleryFinal.getGalleryTheme().getIconFab());
        mTitlebar.setBackgroundColor(com.ez.gallery.GalleryFinal.getGalleryTheme().getTitleBarBgColor());
        mTvTitle.setTextColor(com.ez.gallery.GalleryFinal.getGalleryTheme().getTitleBarTextColor());
        mFabCrop.setColorPressed(com.ez.gallery.GalleryFinal.getGalleryTheme().getFabPressedColor());
        mFabCrop.setColorNormal(com.ez.gallery.GalleryFinal.getGalleryTheme().getFabNornalColor());
    }

    private void findViews() {
        mIvTakePhoto = (ImageView) findViewById(R.id.iv_take_photo);
        mIvCropPhoto = (CropImageView) findViewById(R.id.iv_crop_photo);
        mIvSourcePhoto = (PhotoView) findViewById(R.id.iv_source_photo);
        mLvGallery = (HorizontalListView) findViewById(R.id.lv_gallery);
        mLlGallery = (LinearLayout) findViewById(R.id.ll_gallery);
        mIvBack = (ImageView) findViewById(R.id.iv_back);
        mTvEmptyView = (TextView) findViewById(R.id.tv_empty_view);
        mFabCrop = (FloatingActionButton) findViewById(R.id.fab_crop);
        mIvCrop = (ImageView) findViewById(R.id.iv_crop);
        mIvRotate = (ImageView) findViewById(R.id.iv_rotate);
        mTvTitle = (TextView) findViewById(R.id.tv_title);
        mTitlebar = (LinearLayout) findViewById(R.id.titlebar);
        mIvPreView = (ImageView) findViewById(R.id.iv_preview);
    }

    private void setListener() {
        mIvTakePhoto.setOnClickListener(this);
        mIvBack.setOnClickListener(this);
        mLvGallery.setOnItemClickListener(this);
        mFabCrop.setOnClickListener(this);
        mIvCrop.setOnClickListener(this);
        mIvRotate.setOnClickListener(this);
        mIvPreView.setOnClickListener(this);
    }

    @Override
    protected void takeResult(PhotoInfo info) {
        if (!com.ez.gallery.GalleryFinal.getFunctionConfig().isMutiSelect()) {
            mPhotoList.clear();
            mSelectPhotoList.clear();
        }
        mPhotoList.add(0, info);
        mSelectPhotoList.add(info);
        mPhotoTempMap.put(info.getPhotoId(), new PhotoTempModel(info.getPhotoPath()));
        if (!com.ez.gallery.GalleryFinal.getFunctionConfig().isEditPhoto() && mTakePhotoAction) {
            resultAction();
        } else {
            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isEnablePreview()) {
                mIvPreView.setVisibility(View.VISIBLE);
            }
            mPhotoEditListAdapter.notifyDataSetChanged();

            com.ez.gallery.PhotoSelectActivity activity = (com.ez.gallery.PhotoSelectActivity) ActivityManager.getActivityManager().getActivity(com.ez.gallery.PhotoSelectActivity.class.getName());
            if (activity != null) {
                activity.takeRefreshGallery(info, true);
            }
            loadImage(info);

            hasForceCrop();
        }
    }

    private void loadImage(PhotoInfo photo) {
        mTvEmptyView.setVisibility(View.GONE);
        mIvSourcePhoto.setVisibility(View.VISIBLE);
        mIvCropPhoto.setVisibility(View.GONE);

        String path = "";
        if (photo != null) {
            path = photo.getPhotoPath();
        }
        if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCrop()) {
            setSourceUri(Uri.fromFile(new File(path)));
        }

        com.ez.gallery.GalleryFinal.getCoreConfig().getImageLoader().displayImage(this, path, mIvSourcePhoto, mDefaultDrawable, mScreenWidth, mScreenHeight);
    }

    public void deleteIndexByPreView(int position, PhotoInfo dPhoto) {
        try {
            mPhotoList.remove(position);
            mPhotoEditListAdapter.notifyDataSetChanged();
        } catch (Exception e){}

        deleteIndex(position, dPhoto);
    }

    public void deleteIndex(int position, PhotoInfo dPhoto) {
        if (dPhoto != null) {
            com.ez.gallery.PhotoSelectActivity activity = (com.ez.gallery.PhotoSelectActivity) ActivityManager.getActivityManager().getActivity(com.ez.gallery.PhotoSelectActivity.class.getName());
            if (activity != null) {
                activity.deleteSelect(dPhoto.getPhotoId());
            }

            try {
                for(Iterator<PhotoInfo> iterator = mSelectPhotoList.iterator();iterator.hasNext();){
                    PhotoInfo info = iterator.next();
                    if (info != null && info.getPhotoId() == dPhoto.getPhotoId()) {
                        iterator.remove();
                        break;
                    }
                }
            } catch (Exception e){}
        }

        if (mPhotoList.size() == 0) {
            mSelectIndex = 0;
            mTvEmptyView.setText(R.string.no_photo);
            mTvEmptyView.setVisibility(View.VISIBLE);
            mIvSourcePhoto.setVisibility(View.GONE);
            mIvCropPhoto.setVisibility(View.GONE);
            mIvPreView.setVisibility(View.GONE);
        } else {
            if (position == 0) {
                mSelectIndex = 0;
            } else if (position == mPhotoList.size()) {
                mSelectIndex = position - 1;
            } else {
                mSelectIndex = position;
            }

            PhotoInfo photoInfo = mPhotoList.get(mSelectIndex);
            loadImage(photoInfo);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        mSelectIndex = i;
        PhotoInfo photoInfo = mPhotoList.get(i);
        loadImage(photoInfo);
    }

    @Override
    public void setCropSaveSuccess(final File file) {
        Message message = mHanlder.obtainMessage();
        message.what = CROP_SUC;
        message.obj = file.getAbsolutePath();
        mHanlder.sendMessage(message);
    }

    @Override
    public void setCropSaveException(Throwable throwable) {
        mHanlder.sendEmptyMessage(CROP_FAIL);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.fab_crop) {
            if (mPhotoList.size() == 0) {
                return;
            }
            if (mCropState) {
                System.gc();
                PhotoInfo photoInfo = mPhotoList.get(mSelectIndex);
                try {
                    String ext = FilenameUtils.getExtension(photoInfo.getPhotoPath());
                    File toFile;
                    if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCropReplaceSource()) {
                        toFile = new File(photoInfo.getPhotoPath());
                    } else {
                        toFile = new File(mEditPhotoCacheFile, Utils.getFileName(photoInfo.getPhotoPath()) + "_crop." + ext);
                    }

                    FileUtils.mkdirs(toFile.getParentFile());
                    onSaveClicked(toFile);//保存裁剪
                } catch (Exception e) {
                }
            } else { //完成选择
                resultAction();
            }
        } else if (id == R.id.iv_crop) {

            if (mPhotoList.size() > 0) {
                PhotoInfo photoInfo = mPhotoList.get(mSelectIndex);
                String ext = FilenameUtils.getExtension(photoInfo.getPhotoPath());
                if (StringUtils.isEmpty(ext) || !(ext.equalsIgnoreCase("png") || ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg"))) {
                    toast(getString(R.string.edit_letoff_photo_format));
                } else {
                    if (mCropState) {
                        setCropEnabled(false);

                        corpPageState(false);

                        mTvTitle.setText(R.string.photo_edit);
                    } else {
                        corpPageState(true);
                        setCropEnabled(true);

                        mTvTitle.setText(R.string.photo_crop);
                    }
                    mCropState = !mCropState;
                }
            }
        } else if (id == R.id.iv_rotate) {
            rotatePhoto();
        } else if (id == R.id.iv_take_photo) {
            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isMutiSelect() && com.ez.gallery.GalleryFinal.getFunctionConfig().getMaxSize() == mSelectPhotoList.size()) {
                toast(getString(R.string.select_max_tips));
            } else {
                takePhotoAction();
            }
        } else if (id == R.id.iv_back) {
            if (mCropState && !(mCropPhotoAction && !com.ez.gallery.GalleryFinal.getFunctionConfig().isRotate() && !com.ez.gallery.GalleryFinal.getFunctionConfig().isCamera())) {
                if ((com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCrop() && com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCropEdit())) {
                    mIvCrop.performClick();
                    return;
                }
            }
            finish();
        } else if (id == R.id.iv_preview) {
            Intent intent = new Intent(this, com.ez.gallery.PhotoPreviewActivity.class);
            intent.putExtra(com.ez.gallery.PhotoPreviewActivity.PHOTO_LIST, mSelectPhotoList);
            startActivity(intent);
        }
    }

    private void resultAction() {
        resultData(mSelectPhotoList);
    }

    private void hasForceCrop() {
        if(com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCrop()) {
            mIvCrop.performClick();//进入裁剪状态
            if(!com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCropEdit()) {//强制裁剪后是否可以编辑
                mIvCrop.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 图片旋转
     */
    private void rotatePhoto() {
        if (mPhotoList.size() > 0 && mPhotoList.get(mSelectIndex) != null && !mRotating) {
            final PhotoInfo photoInfo = mPhotoList.get(mSelectIndex);
            final String ext = FilenameUtils.getExtension(photoInfo.getPhotoPath());
            if (StringUtils.isEmpty(ext) || !(ext.equalsIgnoreCase("png") || ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg"))) {
                toast(getString(R.string.edit_letoff_photo_format));
                return;
            }
            mRotating = true;
            if (photoInfo != null) {
                final PhotoTempModel photoTempModel = mPhotoTempMap.get(photoInfo.getPhotoId());
                final String path = photoTempModel.getSourcePath();

                File file;
                if (com.ez.gallery.GalleryFinal.getFunctionConfig().isRotateReplaceSource()) { //裁剪覆盖源文件
                    file = new File(path);
                } else {
                    file = new File(mEditPhotoCacheFile, Utils.getFileName(path) + "_rotate." + ext);
                }

                final File rotateFile = file;
                new AsyncTask<Void, Void, Bitmap>() {
                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        mTvEmptyView.setVisibility(View.VISIBLE);
                        mProgressDialog = ProgressDialog.show(PhotoEditActivity.this, "", getString(R.string.waiting), true, false);
                    }

                    @Override
                    protected Bitmap doInBackground(Void... params) {
                        int orientation;
                        if ( com.ez.gallery.GalleryFinal.getFunctionConfig().isRotateReplaceSource() ) {
                            orientation = 90;
                        } else {
                            orientation = photoTempModel.getOrientation() + 90;
                        }
                        Bitmap bitmap = Utils.rotateBitmap(path, orientation, mScreenWidth, mScreenHeight);
                        if (bitmap != null) {
                            Bitmap.CompressFormat format;
                            if ( ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg") ) {
                                format = Bitmap.CompressFormat.JPEG;
                            } else {
                                format = Bitmap.CompressFormat.PNG;
                            }
                            Utils.saveBitmap(bitmap, format, rotateFile);
                        }
                        return bitmap;
                    }

                    @Override
                    protected void onPostExecute(Bitmap bitmap) {
                        super.onPostExecute(bitmap);
                        if (mProgressDialog != null) {
                            mProgressDialog.dismiss();
                            mProgressDialog = null;
                        }
                        if (bitmap != null) {
                            bitmap.recycle();
                            
                            mTvEmptyView.setVisibility(View.GONE);

                            if ( !com.ez.gallery.GalleryFinal.getFunctionConfig().isRotateReplaceSource() ) {
                                int orientation = photoTempModel.getOrientation() + 90;
                                if (orientation == 360) {
                                    orientation = 0;
                                }
                                photoTempModel.setOrientation(orientation);
                            }

                            Message message = mHanlder.obtainMessage();
                            message.what = UPDATE_PATH;
                            message.obj = rotateFile.getAbsolutePath();
                            mHanlder.sendMessage(message);
                        } else {
                            mTvEmptyView.setText(R.string.no_photo);
                        }
                        loadImage(photoInfo);
                        mRotating = false;
                    }
                }.execute();
            }
        }
    }

    private void corpPageState(boolean crop) {
        if (crop) {
            mIvSourcePhoto.setVisibility(View.GONE);
            mIvCropPhoto.setVisibility(View.VISIBLE);
            mLlGallery.setVisibility(View.GONE);
            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCrop()) {
                mIvCrop.setVisibility(View.VISIBLE);
            }
            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isRotate()) {
                mIvRotate.setVisibility(View.GONE);
            }

            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCamera()) {
                mIvTakePhoto.setVisibility(View.GONE);
            }
        } else {
            mIvSourcePhoto.setVisibility(View.VISIBLE);
            mIvCropPhoto.setVisibility(View.GONE);
            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCrop()) {
                mIvCrop.setVisibility(View.VISIBLE);
            }
            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isRotate()) {
                mIvRotate.setVisibility(View.VISIBLE);
            }

            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isCamera()) {
                mIvTakePhoto.setVisibility(View.VISIBLE);
            }

            if (com.ez.gallery.GalleryFinal.getFunctionConfig().isMutiSelect()) {
                mLlGallery.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RecycleViewBitmapUtils.recycleImageView(mIvCropPhoto);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mCropState && !(mCropPhotoAction && !com.ez.gallery.GalleryFinal.getFunctionConfig().isRotate() && !com.ez.gallery.GalleryFinal.getFunctionConfig().isCamera())) {
                if ((com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCrop() && com.ez.gallery.GalleryFinal.getFunctionConfig().isForceCropEdit())) {
                    mIvCrop.performClick();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
