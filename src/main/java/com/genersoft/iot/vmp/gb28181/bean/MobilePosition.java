package com.genersoft.iot.vmp.gb28181.bean;

import com.genersoft.iot.vmp.gb28181.utils.NumericUtil;
import com.genersoft.iot.vmp.gb28181.utils.SipUtils;
import com.genersoft.iot.vmp.utils.DateUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

import static com.genersoft.iot.vmp.gb28181.utils.XmlUtil.getText;

/**
 * @description: 移动位置bean
 * @author: lawrencehj
 * @date: 2021年1月23日
 */

@Slf4j
@Data
public class MobilePosition {

    /**
     * 通道数据库自增Id
     */
    private Integer channelId;

    /**
     * 通道国标编号
     */
    private String channelDeviceId;

    /**
     * 通知时间
     */
    private long timestamp;

    /**
     * 经度
     */
    private double longitude;

    /**
     * 纬度
     */
    private double latitude;

    /**
     * 海拔高度
     */
    private double altitude;

    /**
     * 速度
     */
    private double speed;

    /**
     * 方向
     */
    private double direction;

    /**
     * 创建时间
     */
    private String createTime;

    public static List<MobilePosition> decode(Element rootElementAfterCharset) {
        // 执法记录仪等设备常把坐标放在 GpsItemList/Item 下
        List<MobilePosition> mobilePositions = new ArrayList<>();
        if (rootElementAfterCharset == null) {
            return mobilePositions;
        }

        Element gpsItemList = rootElementAfterCharset.element("GpsItemList");
        if (gpsItemList != null) {
            List<Element> items = gpsItemList.elements("Item");
            if (items != null && !items.isEmpty()) {
                for (Element item : items) {
                    MobilePosition position = decodeFromElement(item);
                    if (position != null) {
                        mobilePositions.add(position);
                    }
                }
                return mobilePositions;
            }
        }

        MobilePosition mobilePosition = decodeFromElement(rootElementAfterCharset);
        if (mobilePosition != null) {
            mobilePositions.add(mobilePosition);
        }
        return mobilePositions;
    }

    private static MobilePosition decodeFromElement(Element element) {
        if (element == null) {
            return null;
        }

        String longitudeText = getText(element, "Longitude");
        String latitudeText = getText(element, "Latitude");
        if (ObjectUtils.isEmpty(longitudeText) || ObjectUtils.isEmpty(latitudeText)) {
            log.warn("移动位置缺少经纬度字段");
            return null;
        }

        MobilePosition mobilePosition = new MobilePosition();
        mobilePosition.setCreateTime(DateUtil.getNow());

        String channelId = getText(element, "DeviceID");
        mobilePosition.setChannelDeviceId(channelId);

        String time = getText(element, "Time");
        if (ObjectUtils.isEmpty(time)) {
            mobilePosition.setTimestamp(System.currentTimeMillis());
        } else {
            Long timestamp = SipUtils.parseTimeForTimestamp(time);
            if (timestamp == null) {
                log.warn("解析移动位置时间失败：{}， 使用当前时间", time);
                mobilePosition.setTimestamp(System.currentTimeMillis());
            } else {
                mobilePosition.setTimestamp(timestamp);
            }
        }

        try {
            mobilePosition.setLongitude(Double.parseDouble(longitudeText));
            mobilePosition.setLatitude(Double.parseDouble(latitudeText));
        } catch (NumberFormatException e) {
            log.warn("移动位置经纬度无法解析: lon={}, lat={}", longitudeText, latitudeText);
            return null;
        }

        if (NumericUtil.isDouble(getText(element, "Speed"))) {
            mobilePosition.setSpeed(Double.parseDouble(getText(element, "Speed")));
        } else {
            mobilePosition.setSpeed(0.0);
        }
        if (NumericUtil.isDouble(getText(element, "Direction"))) {
            mobilePosition.setDirection(Double.parseDouble(getText(element, "Direction")));
        } else {
            mobilePosition.setDirection(0.0);
        }
        if (NumericUtil.isDouble(getText(element, "Altitude"))) {
            mobilePosition.setAltitude(Double.parseDouble(getText(element, "Altitude")));
        } else {
            mobilePosition.setAltitude(0.0);
        }

        return mobilePosition;
    }

    @Override
    public String toString() {
        return "MobilePosition{" +
                ", channelId=" + channelId +
                ", channelDeviceId='" + channelDeviceId + '\'' +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                ", altitude=" + altitude +
                ", speed=" + speed +
                ", direction=" + direction +
                ", createTime='" + createTime + '\'' +
                '}';
    }
}
