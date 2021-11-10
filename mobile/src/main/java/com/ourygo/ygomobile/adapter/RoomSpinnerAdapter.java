package com.ourygo.ygomobile.adapter;


import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;


import com.ourygo.ygomobile.bean.YGOServer;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class RoomSpinnerAdapter extends BaseAdapter implements SpinnerAdapter
{
	private Context context;
	private List<YGOServer> data;
	private ViewHolder viewHolder;
	private DropDownViewHolder dropDownViewHolder;

	public RoomSpinnerAdapter(Context context, List<YGOServer> data){
		this.context=context;
		this.data=data;
	}

	@Override
	public int getCount(){

		return data.size();
	}

	@Override
	public YGOServer getItem(int p1){

		return data.get(p1);
	}

	@Override
	public long getItemId(int p1){

		return p1;
	}

	@Override
	public View getView(int position, View p2, ViewGroup p3){
		if(p2==null){
			viewHolder=new ViewHolder();
			Log.e("RoomAdp","使用新ADp");
				p2=LayoutInflater.from(context).inflate(R.layout.room_spinner_item,null);
				viewHolder.tv_spinner_text=p2.findViewById(R.id.tv_spinner_text);
			p2.setTag(viewHolder);
		}else{
			viewHolder=(ViewHolder) p2.getTag();
		}
		viewHolder.tv_spinner_text.setText(data.get(position).getName());


		return p2;
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent){
		if(convertView==null){
			dropDownViewHolder=new DropDownViewHolder();

				convertView=LayoutInflater.from(context).inflate(R.layout.room_spinner_dropdown_item,null);
				dropDownViewHolder.tv_spinner_text=convertView.findViewById(R.id.tv_spinner_dropdown_text);

			
			convertView.setTag(dropDownViewHolder);
		}else{
			dropDownViewHolder=(DropDownViewHolder) convertView.getTag();
		}

		
		dropDownViewHolder.tv_spinner_text.setText(data.get(position).getName());

		return convertView;
	}

	public void setNewData(List<YGOServer> serverInfoList) {
		this.data=serverInfoList;
		notifyDataSetInvalidated();
	}

	class DropDownViewHolder{
		TextView tv_spinner_text;
	}

	class ViewHolder{
		TextView tv_spinner_text;
	}

}
