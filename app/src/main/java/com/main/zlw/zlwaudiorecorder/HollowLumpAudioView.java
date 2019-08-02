package com.main.zlw.zlwaudiorecorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.LinkedList;

/**
 * 自定义 View，支持根据音频音量绘制空心块状的波动效果
 *
 * @author biaomingzhong
 * @version V1.0.0 < v5.8.0 新增 >
 * @date 2019-07-31
 */
public class HollowLumpAudioView extends View {
  private static final String TAG = "HollowLumpAudioView";
  private static final int DEFAULT_LUMP_WIDTH = 6;
  private static final int DEFAULT_LUMP_SPACE = 2;
  private static final int DEFAULT_ANIMATE_MS = 300;
  private static final int DEFAULT_LUMP_COLOR = Color.parseColor("#F93351");
  private static final long NANO_SEC_IN_MS = 1000000;

  private int lumpColor = DEFAULT_LUMP_COLOR;
  private int lumpWidth = DEFAULT_LUMP_WIDTH;
  private int lumpSpace = DEFAULT_LUMP_SPACE;
  private int lumpSize;
  private int lumpCount;
  private int lumpMinHeight;
  private int lumpMaxHeight;

  private int viewWidth;
  private int viewHeight;

  private RectF lumpRect = new RectF(0, 0, 0, 0);
  private Paint lumpPaint;
  private LumpData[] lumpDancing;
  private int[] lumpAvailable;
  private int radius = 0;
  private long animateNsUnit = DEFAULT_ANIMATE_MS * NANO_SEC_IN_MS;
  private int maxLumpCount = 11;
  private int minLumpCount = 3;
  private long lastNewTriggerTimeInMs;
  private float newTrigger;
  private LinkedList<LumpData> recycledCache = new LinkedList<>();

  public HollowLumpAudioView(Context context) {
    this(context, null);
  }

  public HollowLumpAudioView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public HollowLumpAudioView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    // TODO: 2019-08-02 xml attrs
    //lumpColor =
    //lumpWidth =
    //lumpSpace =

    lumpMinHeight = lumpWidth;
    //lumpMinHeight =
    //lumpMaxHeight =

    lumpSize = lumpWidth + lumpSpace;
    lumpPaint = new Paint();
    lumpPaint.setAntiAlias(true);
    lumpPaint.setColor(lumpColor);
    lumpPaint.setStyle(Paint.Style.FILL);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    viewWidth = getMeasuredWidth();
    viewHeight = getMeasuredHeight();
    if (lumpMaxHeight <= 0) {
      lumpMaxHeight = viewHeight;
    }
    lumpCount = (int) Math.floor(((float) viewWidth) / lumpSize);
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    ensureArraysCount(lumpCount);

    long nowNs = System.nanoTime();
    Log.i(TAG, "onDraw: " + nowNs);
    boolean isDancingLumpExist = calcDancingLump(nowNs);

    if (newTrigger > 0) {
      addDancingLump(nowNs, calcNewLumpCount(newTrigger), newTrigger);
      isDancingLumpExist = true;
      newTrigger = 0;
    }

    for (int i = 0; i < lumpCount; i++) {
      LumpData lumpItem = lumpDancing[i];
      float lumpHeight = calcLumpHeight(nowNs, lumpMaxHeight, lumpItem);
      lumpRect.left = lumpSize * i;
      lumpRect.top = (lumpMaxHeight - lumpHeight) / 2f;
      lumpRect.right = lumpRect.left + lumpWidth;
      lumpRect.bottom = lumpRect.top + lumpHeight;
      drawRect(canvas, lumpRect);
    }

    if (isDancingLumpExist) {
      invalidate();
    }
  }

  private float calcLumpHeight(long nowNs, int lumpMaxHeight, @Nullable LumpData lumpItem) {
    float result;
    if (lumpItem != null) {
      result =
          (float) (lumpMaxHeight
              * lumpItem.triggerRadio
              * (1f / (lumpItem.weight + 1))
              * Math.sin(Math.PI * (nowNs - lumpItem.startTime) / animateNsUnit));
      Log.i(TAG, "found item calcLumpHeight: " + lumpItem);
      Log.i(TAG, "found item calcLumpHeight result: " + result);
    } else {
      result = lumpMinHeight;
    }
    return result;
  }

  private int calcNewLumpCount(float triggerRadio) {
    return (int) Math.floor(triggerRadio * (maxLumpCount - minLumpCount)) + minLumpCount;
  }

  private void addDancingLump(long nowNs, int newLumpCount, float triggerRadio) {
    Pair<Integer, Integer> newDancingPair = findIndexMaxConsecutiveOnes(lumpAvailable);
    int startIndex = newDancingPair.first;
    int availableLength = newDancingPair.second;
    if (startIndex < 0 || availableLength <= 0) {
      return;
    }

    int middleIndex;
    if (availableLength <= newLumpCount) {
      middleIndex = (int) Math.floor(availableLength / 2f);
      for (int i = 0; i < availableLength; i++) {
        lumpDancing[startIndex + i] =
            cacheGetLumpData(nowNs, Math.abs(i - middleIndex), triggerRadio);
      }
    } else {
      int newLumpStartIndex = startIndex + (int) Math.floor(Math.random() * (availableLength - newLumpCount) / 2f);
      middleIndex = (int) Math.floor(newLumpCount / 2f);
      for (int i = 0; i < newLumpCount; i++) {
        lumpDancing[newLumpStartIndex + i] =
            cacheGetLumpData(nowNs, Math.abs(i - middleIndex), triggerRadio);
      }
    }
  }

  private boolean calcDancingLump(long nowNs) {
    boolean result = false;

    for (int i = 0; i < lumpDancing.length; i++) {
      LumpData lumpVal = lumpDancing[i];
      if (lumpVal == null || lumpVal.startTime <= 0) {
        recycleLumpData(lumpDancing[i]);
        lumpDancing[i] = null;
        lumpAvailable[i] = 1;
        continue;
      }

      long leftTimeInNs = nowNs - lumpVal.startTime;
      if (leftTimeInNs < animateNsUnit) {
        lumpAvailable[i] = 0;
        result = true;
        Log.i(TAG, "calcDancingLump: " + leftTimeInNs);
      } else {
        recycleLumpData(lumpDancing[i]);
        lumpDancing[i] = null;
        lumpAvailable[i] = 1;
      }
    }

    return result;
  }

  private LumpData cacheGetLumpData(long startTime, int weight, float triggerRadio) {
    Log.i(TAG, "cacheGetLumpData: " + weight);
    if (recycledCache.size() > 0) {
      LumpData cache = recycledCache.pop();
      return LumpData.recycle(cache, startTime, weight, triggerRadio);
    }

    return new LumpData(startTime, weight, triggerRadio);
  }

  private void recycleLumpData(@Nullable LumpData lumpData) {
    if (lumpData != null) {
      recycledCache.add(lumpData);
    }
  }

  private void ensureArraysCount(int lumpCount) {
    if (lumpDancing == null) {
      lumpDancing = new LumpData[lumpCount];
      lumpAvailable = new int[lumpCount];
    } else if (lumpDancing.length != lumpCount) {
      LumpData[] currentLumpDancing = lumpDancing;
      lumpDancing = new LumpData[lumpCount];
      lumpAvailable = new int[lumpCount];
      for (int i = 0; i < currentLumpDancing.length && i < lumpDancing.length; i++) {
        lumpDancing[i] = currentLumpDancing[i];
      }
    }
  }

  private void drawRect(Canvas canvas, RectF rect) {
    if (radius > 0) {
      canvas.drawRoundRect(rect, radius, radius, lumpPaint);
    } else {
      canvas.drawRect(rect, lumpPaint);
    }
  }

  public int getRadius() {
    return radius;
  }

  public void setRadius(int radius) {
    this.radius = radius;
  }

  public void addNewTrigger(float newTrigger) {
    long nowMs = System.currentTimeMillis();
    if (lastNewTriggerTimeInMs != 0 && nowMs - lastNewTriggerTimeInMs <= 80) {
      return;
    }
    lastNewTriggerTimeInMs = nowMs;
    this.newTrigger = newTrigger;
    invalidate();
  }

  private static class LumpData {

    private long startTime;
    private int weight;
    private float triggerRadio;

    LumpData(long startTime, int weight, float triggerRadio) {
      this.startTime = startTime;
      this.weight = weight;
      this.triggerRadio = triggerRadio;
    }

    static LumpData recycle(LumpData cache, long startTime, int weight, float triggerRadio) {
      cache.startTime = startTime;
      cache.weight = weight;
      cache.triggerRadio = triggerRadio;
      return cache;
    }

    @Override public String toString() {
      return "LumpData{" +
          "startTime=" + startTime +
          ", weight=" + weight +
          ", triggerRadio=" + triggerRadio +
          '}';
    }
  }

  /**
   * 查找数组中连续 1 的序列
   *
   * @param numbers 只有 0 和 1 的数组
   * @return 最长连续 1 序列的子序列的开始索引，以及长度
   */
  @NonNull
  public static Pair<Integer, Integer> findIndexMaxConsecutiveOnes(int[] numbers) {
    int consecutiveLength = 0;
    int count = 0;
    int result = -1;

    for (int i = 0; i < numbers.length; i++)
      if (numbers[i] == 1) {
        count++;
        if (count > consecutiveLength) {
          consecutiveLength = count;
          result = i - count + 1;
        }
      } else {
        count = 0;
      }

    return new Pair<>(result, consecutiveLength);
  }
}
