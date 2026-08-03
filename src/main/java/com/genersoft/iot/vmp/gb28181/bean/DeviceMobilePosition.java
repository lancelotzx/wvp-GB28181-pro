package com.genersoft.iot.vmp.gb28181.bean;

import com.genersoft.iot.vmp.gb28181.utils.NumericUtil;
import com.genersoft.iot.vmp.gb28181.utils.SipUtils;
import com.genersoft.iot.vmp.utils.DateUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

import static com.genersoft.iot.vmp.gb28181.utils.XmlUtil.getText;

/**
 * 国标设备移动位置
 */

@Slf4j
@Getter
@Setter
public class DeviceMobilePosition extends MobilePosition{

    /**
     * 通道国标编号
     */
    private String channelDeviceId;


    private Device device;


    public static List<DeviceMobilePosition> decode(Device device, Element rootElementAfterCharset) {
        List<DeviceMobilePosition> mobilePositions = new ArrayList<>();
        if (rootElementAfterCharset == null) {
            return mobilePositions;
        }

        // 执法记录仪等设备常把坐标放在 GpsItemList/Item 下，而不是 Notify 根节点
        Element gpsItemList = rootElementAfterCharset.element("GpsItemList");
        if (gpsItemList != null) {
            List<Element> items = gpsItemList.elements("Item");
            if (items != null && !items.isEmpty()) {
                for (Element item : items) {
                    DeviceMobilePosition position = decodeFromElement(device, item);
                    if (position != null) {
                        mobilePositions.add(position);
                    }
                }
                return mobilePositions;
            }
        }

        DeviceMobilePosition mobilePosition = decodeFromElement(device, rootElementAfterCharset);
        if (mobilePosition != null) {
            mobilePositions.add(mobilePosition);
        }
        return mobilePositions;
    }

    private static DeviceMobilePosition decodeFromElement(Device device, Element element) {
        if (element == null) {
            return null;
        }

        String longitudeText = getText(element, "Longitude");
        String latitudeText = getText(element, "Latitude");
        if (ObjectUtils.isEmpty(longitudeText) || ObjectUtils.isEmpty(latitudeText)) {
            log.warn("移动位置缺少经纬度字段, device={}",
                    device != null ? device.getDeviceId() : null);
            return null;
        }

        DeviceMobilePosition mobilePosition = new DeviceMobilePosition();
        mobilePosition.setCreateTime(DateUtil.getNow());
        mobilePosition.setDevice(device);

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
            log.warn("移动位置经纬度无法解析: lon={}, lat={}, device={}",
                    longitudeText, latitudeText,
                    device != null ? device.getDeviceId() : null);
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
        return "DeviceMobilePosition{" +
                "channelDeviceId='" + channelDeviceId + '\'' +
                ", deviceId='" + (device != null ? device.getDeviceId() : null) + '\'' +
                "} " + super.toString();
    }
}
