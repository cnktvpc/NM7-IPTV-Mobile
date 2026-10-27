package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;
/** Mobile playback must distinguish a media end from source/period changes. */
public interface Nm7PlaybackEndOwner {
    boolean hasNm7MediaEnded();
    boolean keepNm7EndedMini();
}
