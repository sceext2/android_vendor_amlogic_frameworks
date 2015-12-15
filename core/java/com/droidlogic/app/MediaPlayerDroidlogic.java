/*
 * AMLOGIC Media Player.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the named License,
 * or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA
 *
 * Author:  Wang Jian <jian.wang@amlogic.com>
 * Modified: XiaoLiang.Wang <xiaoliang.wang@amlogic.com 20151202>
 */
package com.droidlogic.app;

import android.content.Context;
import android.net.Uri;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import java.lang.Thread.State;

import java.util.Map;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import java.io.IOException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Created by wangjian on 2014/4/17.
 */
public class MediaPlayerDroidlogic extends MediaPlayer {
    private static final String TAG = "MediaPlayerDroidlogic";
    private static final boolean DEBUG = false;
    private static final int FF_PLAY_TIME = 5000;
    private static final int FB_PLAY_TIME = 5000;
    private static final int BASE_SLEEP_TIME = 500;
    private static final int ON_FF_COMPLETION = 1;
    private int mStep = 0;
    private boolean mIsFF = true;
    private boolean mStopFast = true;
    private int mPos = -1;
    private Thread mThread = null;
    private OnCompletionListener mOnCompletionListener = null;
    private OnSeekCompleteListener mOnSeekCompleteListener = null;
    private OnErrorListener mOnErrorListener = null;

    //must sync with IMediaPlayer.cpp (av\media\libmedia)
    private IBinder mIBinder = null; //IMediaPlayer
    private IBinder mIBinderService = null; //IMediaPlayerService
    private static final String SYS_TOKEN                   = "android.media.IMediaPlayer";
    private static final String SYS_TOKEN_SERVICE           = "android.media.IMediaPlayerService";
    private static final int CREATE                         = IBinder.FIRST_CALL_TRANSACTION;
    private static final int DISCONNECT                     = IBinder.FIRST_CALL_TRANSACTION;
    private static final int SET_DATA_SOURCE_URL            = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int SET_DATA_SOURCE_FD             = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int SET_DATA_SOURCE_STREAM         = IBinder.FIRST_CALL_TRANSACTION + 3;
    private static final int SET_DATA_SOURCE_CALLBACK       = IBinder.FIRST_CALL_TRANSACTION + 4;
    private static final int PREPARE_ASYNC                  = IBinder.FIRST_CALL_TRANSACTION + 5;
    private static final int START                          = IBinder.FIRST_CALL_TRANSACTION + 6;
    private static final int STOP                           = IBinder.FIRST_CALL_TRANSACTION + 7;
    private static final int IS_PLAYING                     = IBinder.FIRST_CALL_TRANSACTION + 8;
    private static final int SET_PLAYBACK_SETTINGS          = IBinder.FIRST_CALL_TRANSACTION + 9;
    private static final int GET_PLAYBACK_SETTINGS          = IBinder.FIRST_CALL_TRANSACTION + 10;
    private static final int SET_SYNC_SETTINGS              = IBinder.FIRST_CALL_TRANSACTION + 11;
    private static final int GET_SYNC_SETTINGS              = IBinder.FIRST_CALL_TRANSACTION + 12;
    private static final int PAUSE                          = IBinder.FIRST_CALL_TRANSACTION + 13;
    private static final int SEEK_TO                        = IBinder.FIRST_CALL_TRANSACTION + 14;
    private static final int GET_CURRENT_POSITION           = IBinder.FIRST_CALL_TRANSACTION + 15;
    private static final int GET_DURATION                   = IBinder.FIRST_CALL_TRANSACTION + 16;
    private static final int RESET                          = IBinder.FIRST_CALL_TRANSACTION + 17;
    private static final int SET_AUDIO_STREAM_TYPE          = IBinder.FIRST_CALL_TRANSACTION + 18;
    private static final int SET_LOOPING                    = IBinder.FIRST_CALL_TRANSACTION + 19;
    private static final int SET_VOLUME                     = IBinder.FIRST_CALL_TRANSACTION + 20;
    private static final int INVOKE                         = IBinder.FIRST_CALL_TRANSACTION + 21;
    private static final int SET_METADATA_FILTER            = IBinder.FIRST_CALL_TRANSACTION + 22;
    private static final int GET_METADATA                   = IBinder.FIRST_CALL_TRANSACTION + 23;
    private static final int SET_AUX_EFFECT_SEND_LEVEL      = IBinder.FIRST_CALL_TRANSACTION + 24;
    private static final int ATTACH_AUX_EFFECT              = IBinder.FIRST_CALL_TRANSACTION + 25;
    private static final int SET_VIDEO_SURFACETEXTURE       = IBinder.FIRST_CALL_TRANSACTION + 26;
    private static final int SET_PARAMETER                  = IBinder.FIRST_CALL_TRANSACTION + 27;
    private static final int GET_PARAMETER                  = IBinder.FIRST_CALL_TRANSACTION + 28;
    private static final int SET_RETRANSMIT_ENDPOINT        = IBinder.FIRST_CALL_TRANSACTION + 29;
    private static final int GET_RETRANSMIT_ENDPOINT        = IBinder.FIRST_CALL_TRANSACTION + 30;
    private static final int SET_NEXT_PLAYER                = IBinder.FIRST_CALL_TRANSACTION + 31;

    //must sync with Mediaplayer.h (av\include\media)
    public static final int KEY_PARAMETER_AML_VIDEO_POSITION_INFO           = 2000;
    public static final int KEY_PARAMETER_AML_PLAYER_TYPE_STR               = 2001;
    public static final int KEY_PARAMETER_AML_PLAYER_VIDEO_OUT_TYPE         = 2002;
    public static final int KEY_PARAMETER_AML_PLAYER_SWITCH_SOUND_TRACK     = 2003;             //refer to lmono,rmono,stereo,set only
    public static final int KEY_PARAMETER_AML_PLAYER_SWITCH_AUDIO_TRACK     = 2004;             //refer to audio track index,set only
    public static final int KEY_PARAMETER_AML_PLAYER_TRICKPLAY_FORWARD      = 2005;             //refer to forward:speed
    public static final int KEY_PARAMETER_AML_PLAYER_TRICKPLAY_BACKWARD     = 2006;             //refer to backward:speed
    public static final int KEY_PARAMETER_AML_PLAYER_FORCE_HARD_DECODE      = 2007;             //refer to mp3,etc.
    public static final int KEY_PARAMETER_AML_PLAYER_FORCE_SOFT_DECODE      = 2008;             //refer to mp3,etc.
    public static final int KEY_PARAMETER_AML_PLAYER_GET_MEDIA_INFO         = 2009;             //get media info
    public static final int KEY_PARAMETER_AML_PLAYER_FORCE_SCREEN_MODE      = 2010;             //set screen mode
    public static final int KEY_PARAMETER_AML_PLAYER_SET_DISPLAY_MODE       = 2011;             //set display mode
    public static final int KEY_PARAMETER_AML_PLAYER_GET_DTS_ASSET_TOTAL    = 2012;             //get dts asset total number
    public static final int KEY_PARAMETER_AML_PLAYER_SET_DTS_ASSET          = 2013;             //set dts asset
    public static final int KEY_PARAMETER_AML_PLAYER_HWBUFFER_STATE         = 3001;             //refer to stream buffer info, hardware decoder buffer infos,get only
    public static final int KEY_PARAMETER_AML_PLAYER_RESET_BUFFER           = 8000;             //top level seek..player need to reset & clearbuffers
    public static final int KEY_PARAMETER_AML_PLAYER_FREERUN_MODE           = 8002;             //play ASAP...
    public static final int KEY_PARAMETER_AML_PLAYER_ENABLE_OSDVIDEO        = 8003;             //play enable osd video for this player....
    public static final int KEY_PARAMETER_AML_PLAYER_DIS_AUTO_BUFFER        = 8004;             //play ASAP...
    public static final int KEY_PARAMETER_AML_PLAYER_ENA_AUTO_BUFFER        = 8005;             //play ASAP...
    public static final int KEY_PARAMETER_AML_PLAYER_USE_SOFT_DEMUX         = 8006;             //play use soft demux
    public static final int KEY_PARAMETER_AML_PLAYER_PR_CUSTOM_DATA         = 9001;             //string, playready, set only

    public MediaPlayerDroidlogic() {

    }

    /**
     * The function should be invoked after a player was created. (after setDataSource)
     * In this case, the client (data.writeStrongBinder(null)) will get the running one.
     * Otherwise will throw "unknow error"
     */
    private void prepareIBinder() {
        try {
            Object object = Class.forName("android.os.ServiceManager")
                .getMethod("getService", new Class[] { String.class })
                .invoke(null, new Object[] { "media.player" });
            mIBinderService = (IBinder)object;
            if (DEBUG) Log.i(TAG,"[MediaPlayerDroidlogic]mIBinderService:" + mIBinderService);
        }
        catch (Exception ex) {
            Log.e(TAG, "get IMediaPlayerService:" + ex);
        }

        try {
            if (null != mIBinderService) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                data.writeInterfaceToken(SYS_TOKEN_SERVICE);
                data.writeStrongBinder(null);
                data.writeInt(0);
                mIBinderService.transact(CREATE, data, reply, 0);
                mIBinder = reply.readStrongBinder();
                reply.recycle();
                data.recycle();
                if (DEBUG) Log.i(TAG,"[MediaPlayerDroidlogic]mIBinder:" + mIBinder);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "get IMediaPlayer :" + ex);
        }
    }

    private void getParameter(int key, Parcel reply) {
        if (DEBUG) Log.i(TAG,"[getParameter]mIBinder:" + mIBinder + ",key:" + key + ",reply:" + reply);
        if (null == mIBinder) {
            prepareIBinder();
        }

        try {
            if (null != mIBinder) {
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken(SYS_TOKEN);
                data.writeInt(key);
                mIBinder.transact(GET_PARAMETER, data, reply, 0);
                data.recycle();
                return;
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "getParameter:" + ex);
        }

        return;
    }

    public Parcel getParcelParameter(int key) {
        Parcel p = Parcel.obtain();
        getParameter(key, p);
        return p;
    }

    public String getStringParameter(int key) {
        Parcel p = Parcel.obtain();
        getParameter(key, p);
        String ret = p.readString();
        p.recycle();
        return ret;
    }

    public int getIntParameter(int key) {
        Parcel p = Parcel.obtain();
        getParameter(key, p);
        int ret = p.readInt();
        p.recycle();
        return ret;
    }

    private boolean setParameter(int key, Parcel value) {
        boolean ret = false;
        if (DEBUG) Log.i(TAG,"[setParameter]mIBinder:" + mIBinder + ",key:" + key + ",Parcel value:" + value);
        if (null == mIBinder) {
            prepareIBinder();
        }

        try {
            if (null != mIBinder) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                data.writeInterfaceToken(SYS_TOKEN);
                data.writeInt(key);
                if (value.dataSize() > 0) {
                    data.appendFrom(value, 0, value.dataSize());
                }
                mIBinder.transact(SET_PARAMETER, data, reply, 0);
                if (0 == reply.readInt()) { //OK
                    ret = true;
                }
                else {
                    ret = false;
                }
                reply.recycle();
                data.recycle();
                return ret;
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "setParameter:" + ex);
        }

        return false;
    }

    public boolean setParameter(int key, String value) {
        if (DEBUG) Log.i(TAG,"[setParameter]mIBinder:" + mIBinder + ",key:" + key + ",String value:" + value);
        Parcel p = Parcel.obtain();
        p.writeString(value);
        boolean ret = setParameter(key, p);
        p.recycle();
        return ret;
    }

    public boolean setParameter(int key, int value) {
        if (DEBUG) Log.i(TAG,"[setParameter]mIBinder:" + mIBinder + ",key:" + key + ",int value:" + value);
        Parcel p = Parcel.obtain();
        p.writeInt(value);
        boolean ret = setParameter(key, p);
        p.recycle();
        return ret;
    }

    public class VideoInfo{
        public int index;
        public int id;
        public String vformat;
        public int width;
        public int height;
    }

    public class AudioInfo{
        public int index;
        public int id; //id is useless for application
        public int aformat;
        public int channel;
        public int sample_rate;
    }

    public class SubtitleInfo{
        public int index;
        public int id;
        public int sub_type;
        public String sub_language;
    }

    public class MediaInfo{
        public String filename;
        public int duration;
        public String file_size;
        public int bitrate;
        public int type;
        //public int fps;
        public int cur_video_index;
        public int cur_audio_index;
        public int cur_sub_index;

        public int total_video_num;
        public VideoInfo[] videoInfo;

        public int total_audio_num;
        public AudioInfo[] audioInfo;

        public int total_sub_num;
        public SubtitleInfo[] subtitleInfo;
    }

    public MediaInfo getMediaInfo() {
        MediaInfo mediaInfo = new MediaInfo();
        Parcel p = Parcel.obtain();
        getParameter(KEY_PARAMETER_AML_PLAYER_GET_MEDIA_INFO, p);
        mediaInfo.filename = p.readString();
        mediaInfo.duration = p.readInt();
        mediaInfo.file_size = p.readString();
        mediaInfo.bitrate = p.readInt();
        mediaInfo.type = p.readInt();
        //mediaInfo.fps = p.readInt();
        mediaInfo.cur_video_index = p.readInt();
        mediaInfo.cur_audio_index = p.readInt();
        mediaInfo.cur_sub_index = p.readInt();
        if (DEBUG) Log.i(TAG,"[getMediaInfo]filename:"+mediaInfo.filename+",duration:"+mediaInfo.duration+",file_size:"+mediaInfo.file_size+",bitrate:"+mediaInfo.bitrate+",type:"+mediaInfo.type);
        if (DEBUG) Log.i(TAG,"[getMediaInfo]cur_video_index:"+mediaInfo.cur_video_index+",cur_audio_index:"+mediaInfo.cur_audio_index+",cur_sub_index:"+mediaInfo.cur_sub_index);

        //----video info----
        mediaInfo.total_video_num = p.readInt();
        if (DEBUG) Log.i(TAG,"[getMediaInfo]mediaInfo.total_video_num:"+mediaInfo.total_video_num);
        mediaInfo.videoInfo = new VideoInfo[mediaInfo.total_video_num];
        for (int i=0;i<mediaInfo.total_video_num;i++) {
            mediaInfo.videoInfo[i] = new VideoInfo();
            mediaInfo.videoInfo[i].index = p.readInt();
            mediaInfo.videoInfo[i].id = p.readInt();
            mediaInfo.videoInfo[i].vformat = p.readString();
            mediaInfo.videoInfo[i].width = p.readInt();
            mediaInfo.videoInfo[i].height = p.readInt();
            if (DEBUG) Log.i(TAG,"[getMediaInfo]videoInfo i:"+i+",index:"+mediaInfo.videoInfo[i].index+",id:"+mediaInfo.videoInfo[i].id);
            if (DEBUG) Log.i(TAG,"[getMediaInfo]videoInfo i:"+i+",vformat:"+mediaInfo.videoInfo[i].vformat);
            if (DEBUG) Log.i(TAG,"[getMediaInfo]videoInfo i:"+i+",width:"+mediaInfo.videoInfo[i].width+",height:"+mediaInfo.videoInfo[i].height);
        }

        //----audio info----
        mediaInfo.total_audio_num = p.readInt();
        if (DEBUG) Log.i(TAG,"[getMediaInfo]mediaInfo.total_audio_num:"+mediaInfo.total_audio_num);
        mediaInfo.audioInfo = new AudioInfo[mediaInfo.total_audio_num];
        for (int j=0;j<mediaInfo.total_audio_num;j++) {
            mediaInfo.audioInfo[j] = new AudioInfo();
            mediaInfo.audioInfo[j].index = p.readInt();
            mediaInfo.audioInfo[j].id = p.readInt();
            mediaInfo.audioInfo[j].aformat = p.readInt();
            mediaInfo.audioInfo[j].channel = p.readInt();
            mediaInfo.audioInfo[j].sample_rate = p.readInt();
            if (DEBUG) Log.i(TAG,"[getMediaInfo]audioInfo j:"+j+",index:"+mediaInfo.audioInfo[j].index+",id:"+mediaInfo.audioInfo[j].id+",aformat:"+mediaInfo.audioInfo[j].aformat);
            if (DEBUG) Log.i(TAG,"[getMediaInfo]audioInfo j:"+j+",channel:"+mediaInfo.audioInfo[j].channel+",sample_rate:"+mediaInfo.audioInfo[j].sample_rate);
        }

        //----subtitle info----
        mediaInfo.total_sub_num = p.readInt();
        if (DEBUG) Log.i(TAG,"[getMediaInfo]mediaInfo.total_sub_num:"+mediaInfo.total_sub_num);
        mediaInfo.subtitleInfo = new SubtitleInfo[mediaInfo.total_sub_num];
        for (int k=0;k<mediaInfo.total_sub_num;k++) {
            mediaInfo.subtitleInfo[k] = new SubtitleInfo();
            mediaInfo.subtitleInfo[k].index = p.readInt();
            mediaInfo.subtitleInfo[k].id = p.readInt();
            mediaInfo.subtitleInfo[k].sub_type = p.readInt();
            mediaInfo.subtitleInfo[k].sub_language = p.readString();
            if (DEBUG) Log.i(TAG,"[getMediaInfo]subtitleInfo k:"+k+",index:"+mediaInfo.subtitleInfo[k].index+",id:"+mediaInfo.subtitleInfo[k].id+",sub_type:"+mediaInfo.subtitleInfo[k].sub_type);
            if (DEBUG) Log.i(TAG,"[getMediaInfo]subtitleInfo k:"+k+",sub_language:"+mediaInfo.subtitleInfo[k].sub_language);
        }
        p.recycle();

        return mediaInfo;
    }

    public void fastForward (int step) {
        if (DEBUG) Log.i (TAG, "fastForward:" + step);
        synchronized (this) {
            String playerTypeStr = getStringParameter(KEY_PARAMETER_AML_PLAYER_TYPE_STR);
            if ( (playerTypeStr != null) && (playerTypeStr.equals ("AMLOGIC_PLAYER"))) {
                String str = Integer.toString (step);
                StringBuilder builder = new StringBuilder();
                builder.append ("forward:" + str);
                if (DEBUG) Log.i (TAG, "[HW]" + builder.toString());
                setParameter(KEY_PARAMETER_AML_PLAYER_TRICKPLAY_FORWARD,builder.toString());
                return;
            }
            mStep = step;
            mIsFF = true;
            mStopFast = false;
            if (mThread == null) {
                mThread = new Thread (runnable);
                mThread.start();
            }
            else {
                if (mThread.getState() == State.TERMINATED) {
                    mThread = new Thread (runnable);
                    mThread.start();
                }
            }
        }
    }

    public void fastBackward (int step) {
        if (DEBUG) Log.i (TAG, "fastBackward:" + step);
        synchronized (this) {
            String playerTypeStr = getStringParameter(KEY_PARAMETER_AML_PLAYER_TYPE_STR);
            if ( (playerTypeStr != null) && (playerTypeStr.equals ("AMLOGIC_PLAYER"))) {
                String str = Integer.toString (step);
                StringBuilder builder = new StringBuilder();
                builder.append ("backward:" + str);
                if (DEBUG) Log.i (TAG, "[HW]" + builder.toString());
                setParameter(KEY_PARAMETER_AML_PLAYER_TRICKPLAY_BACKWARD,builder.toString());
                return;
            }
            mStep = step;
            mIsFF = false;
            mStopFast = false;
            if (mThread == null) {
                mThread = new Thread (runnable);
                mThread.start();
            }
            else {
                if (mThread.getState() == State.TERMINATED) {
                    mThread = new Thread (runnable);
                    mThread.start();
                }
            }
        }
    }

    public boolean isPlaying() {
        if (!mStopFast) {
            return true;
        }
        return super.isPlaying();
    }

    public void reset() {
        mIBinder = null;
        mStopFast = true;
        super.reset();
    }

    public void start() {
        mStopFast = true;
        super.start();
    }

    public void pause() {
        mStopFast = true;
        super.pause();
    }

    public void stop() {
        mStopFast = true;
        super.stop();
    }

    public void setOnSeekCompleteListener (OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
        super.setOnSeekCompleteListener (mMediaPlayerSeekCompleteListener);
    }

    public void setOnCompletionListener (OnCompletionListener listener) {
        mOnCompletionListener = listener;
        super.setOnCompletionListener (mMediaPlayerCompletionListener);
    }

    public void setOnErrorListener (OnErrorListener listener) {
        mOnErrorListener = listener;
        super.setOnErrorListener (mMediaPlayerErrorListener);
    }

    private void superPause() {
        super.pause();
    }

    private void superStart() {
        super.start();
    }

    private void OnFFCompletion() {
        if (mOnCompletionListener != null) {
            if (DEBUG) Log.i (TAG, "mOnCompletionListener.onCompletion");
            mOnCompletionListener.onCompletion (this);
        }
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            int pos;
            int duration = getDuration ();
            int sleepTime = BASE_SLEEP_TIME;
            int seekPos = 0;
            superPause();
            while (!mStopFast) {
                if (mStep < 1) {
                    mStopFast = true;
                    superStart();
                    break;
                }
                pos = getCurrentPosition();
                if (pos == 0) {
                    mStopFast = true;
                    superStart();
                    break;
                }
                if (pos == duration || pos == mPos) {
                    stop();
                    Message newMsg = Message.obtain();
                    newMsg.what = ON_FF_COMPLETION;
                    mMainHandler.sendMessage (newMsg);
                    break;
                }
                mPos = pos;
                if (mIsFF) {
                    int jumpTime = mStep * FF_PLAY_TIME;
                    int baseTime = 0;
                    if (mPos < seekPos + sleepTime) {
                        baseTime = seekPos + sleepTime;
                    }
                    else {
                        baseTime = mPos;
                    }
                    seekPos = (baseTime + jumpTime) > duration ? duration : (baseTime + jumpTime);
                    seekTo (seekPos);
                }
                else {
                    int jumpTime = mStep * FB_PLAY_TIME;
                    seekPos = (mPos - jumpTime) < 0 ? 0 : (mPos - jumpTime);
                    seekTo (seekPos);
                }
                try {
                    Thread.sleep (sleepTime);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    };
    private MediaPlayer.OnSeekCompleteListener mMediaPlayerSeekCompleteListener =
    new MediaPlayer.OnSeekCompleteListener() {
        public void onSeekComplete (MediaPlayer mp) {
            if (mStopFast) {
                if (mOnSeekCompleteListener != null) {
                    mOnSeekCompleteListener.onSeekComplete (mp);
                }
            }
        }
    };

    private MediaPlayer.OnCompletionListener mMediaPlayerCompletionListener =
    new MediaPlayer.OnCompletionListener() {
        public void onCompletion (MediaPlayer mp) {
            if (!mStopFast) {
                mStopFast = true;
            }
            if (mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion (mp);
            }
        }
    };

    private MediaPlayer.OnErrorListener mMediaPlayerErrorListener =
    new MediaPlayer.OnErrorListener() {
        public boolean onError (MediaPlayer mp, int what, int extra) {
            if (!mStopFast) {
                mStopFast = true;
            }
            if (mOnErrorListener != null) {
                return mOnErrorListener.onError (mp, what, extra);
            }
            return true;
        }
    };
    private Handler mMainHandler = new Handler() {
        public void handleMessage (Message msg) {
            switch (msg.what) {
                case ON_FF_COMPLETION:
                    OnFFCompletion();
                    break;
            }
        }
    };
}

