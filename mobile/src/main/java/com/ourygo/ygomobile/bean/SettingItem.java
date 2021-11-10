package com.ourygo.ygomobile.bean;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.ourygo.ygomobile.util.OYUtil;

import cn.garymb.ygomobile.lite.R;

public class SettingItem implements MultiItemEntity {

    public static final int ITEM_SAME = 0;
    public static final int ITEM_SWITCH = 1;
    public static final int ITEM_IMAGE_SELECT = 2;

    public static final int ICON_NULL = 0;

    private String name;
    //item类型唯一
    private int itemType;

    private String groupName;
    private int groupId;

    //item唯一标识
    private int id;
    private String message;
    private int nameColor;
    private int messageColor;
    private boolean isNext;
    private int icon;
    private Object object;
    private boolean isLoading;
    private boolean isContent;

    public SettingItem() {
        icon = ICON_NULL;
        nameColor=OYUtil.c(R.color.black);
        messageColor=OYUtil.c(R.color.blackLight);
        isNext=true;
        isLoading=false;
        isContent=true;
    }

    public static SettingItem toSettingItem(int id, String name, int typeId, int groupId, String groupName) {
        SettingItem si = new SettingItem();
        si.setId(id);
        si.setName(name);
        si.setItemType(typeId);
        si.setGroupId(groupId);
        si.setGroupName(groupName);
        return si;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }

    public boolean isContent() {
        return isContent;
    }

    public void setContent(boolean content) {
        isContent = content;
    }

    public static SettingItem toSettingItem(int id, String name, int typeId, int groupId) {
        return toSettingItem(id, name, typeId, groupId, null);
    }

    public static SettingItem toSettingItem(int id, String name, int groupId) {
        return toSettingItem(id, name, groupId, null);
    }

    public static SettingItem toSettingItem(int id, String name, int groupId, String typeName) {
        return toSettingItem(id, name, ITEM_SAME, groupId, typeName);
    }

    public int getMessageColor() {
        return messageColor;
    }

    public void setMessageColor(int messageColor) {
        this.messageColor = messageColor;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isNext() {
        return isNext;
    }

    public void setNext(boolean next) {
        isNext = next;
    }

    public int getNameColor() {
        return nameColor;
    }

    public void setNameColor(int nameColor) {
        this.nameColor = nameColor;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getItemType() {
        return itemType;
    }

    public void setItemType(int itemType) {
        this.itemType = itemType;
    }
}
