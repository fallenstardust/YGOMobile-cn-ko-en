package com.ourygo.ygomobile.util;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.App;

public class FileUtil
{
	//------------------------------------文件相关方法------------------------------------------------------------------------

	//获取指定目录下所有文件夹的绝对路径
	public static List<String> getFolder(String s){
		List<String> list=new ArrayList<String>();
		File[] f=new File(s).listFiles();

		for(File ff:f){
			if(ff.isDirectory()){
				list.add(ff.getAbsolutePath());
			}

		}
		return list;
	}
	
	//获取路径文件名
	public static String getFilename(String s){
		if(s!=null){
			String[] ss=s.split("/");
			return ss[ss.length-1];
		}
		return null;
	}


	//删除文件
	public static boolean delFile(String s){
		File file=new File(s);
		
		if(file.isDirectory()){
			boolean b=true;
			for(File ss:file.listFiles()){
				Log.e("删除","删除"+ss.getPath());
				b&=delFile(ss.getPath());
			}
			b&=file.delete();
			return b;
		}else{
		//如果文件路径所对应的文件存在,并且是一个文件,则直接删除
		if(file.exists()&&file.isFile()){
			Log.e("正在删除","正在删除"+file.getPath());
			if(file.delete()){
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
		}
	}


	//重命名文件
	public static String reFileName(String Path, String name){

		File file=new File(Path);
		String rePath=null;
		if(file.exists())
		{
			//获取文件名以外的路径
			String abb[]=Path.split("/");
			String lj="";
			for(int i=0;i<abb.length-1;i++){
				lj+=abb[i]+"/";
			}
			rePath= lj+name;
			//重命名
			file.renameTo(new File(rePath));
		}
		return rePath;
	}

	public static void copyFile(String oldPath, String newPath, boolean isname) throws FileNotFoundException, IOException {

		//判断复制后的路径是否含有文件名,如果没有则加上
		if (!isname) {
			//由于newPath是路径加文件名,所以获取要复制的文件名与复制后的路径组成新的newPath
			String abb[]=oldPath.split("/");
			newPath = newPath + "/" + abb[abb.length - 1];
		}

		FileInputStream fis=new FileInputStream(oldPath);
		FileOutputStream fos=new FileOutputStream(newPath);
		byte[] buf=new byte[1024];
		int len=0;
		while ((len = fis.read(buf)) != -1) {
			fos.write(buf, 0, len);
		}
		fos.close();
		fis.close();

	}
	
	public static void moveFile(String oldPath, String newPath, boolean isname) throws FileNotFoundException, IOException {

		//判断复制后的路径是否含有文件名,如果没有则加上
		if (!isname) {
			//由于newPath是路径加文件名,所以获取要复制的文件名与复制后的路径组成新的newPath
			String abb[]=oldPath.split("/");
			newPath = newPath + "/" + abb[abb.length - 1];
		}

		FileInputStream fis=new FileInputStream(oldPath);
		FileOutputStream fos=new FileOutputStream(newPath);
		byte[] buf=new byte[1024];
		int len=0;
		while ((len = fis.read(buf)) != -1) {
			fos.write(buf, 0, len);
		}
		fos.close();
		fis.close();
		//删除文件
		File file=new File(oldPath);
		if (file.exists() && file.isFile()) {
			file.delete();
		}

	}

	/*
	 *检查文件夹是否存在,不存在则创建
	 *path:文件夹路径
	 */
	public static void directoryCreate(String path){
		File f=new File(path);
		if(!f.exists()||!f.isDirectory()){
			f.mkdir();
		}
	}

	/*
	 *检查文件是否存在,不存在则创建
	 *path:文件路径
	 */
	public static void fileCreate(String path) throws IOException {
		File f=new File(path);
		if(!f.exists()){
			f.createNewFile();
		}
	}
	
	/*复制asseta文件夹里面的文件到指定文件夹
	 *name:文件名
	 **path:要复制到的文件夹
	 *
	 */
	public static void copyAssets(final String name, final String path) throws FileNotFoundException, IOException {


		//assets中文件名字
		String fileName = name;
		//拿到输入流
		InputStream in = App.get().getAssets().open(fileName);
		//打开输出流
		FileOutputStream out = new FileOutputStream(path+fileName);
		int len = -1 ;
		byte[] bytes = new byte[1024];
		//不断读取输入流
		while ((len = in.read(bytes)) != -1)
		{
			//写到输出流中
			out.write(bytes, 0, len);
		}
		out.close();
		in.close();

	}
	
}
