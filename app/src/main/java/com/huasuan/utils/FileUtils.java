package com.huasuan.utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


public class FileUtils {
    private static final String TAG = "FileUtil";

    public static final int S_IRWXU = 00700;
    public static final int S_IRUSR = 00400;
    public static final int S_IWUSR = 00200;
    public static final int S_IXUSR = 00100;

    public static final int S_IRWXG = 00070;
    public static final int S_IRGRP = 00040;
    public static final int S_IWGRP = 00020;
    public static final int S_IXGRP = 00010;

    public static final int S_IRWXO = 00007;
    public static final int S_IROTH = 00004;
    public static final int S_IWOTH = 00002;
    public static final int S_IXOTH = 00001;

    /** 在给定路径的后面附加文件或者目录。 */
    public static String pathAppend(String path, String more) {
        if (path == null) {
            path = "";
        }
        if (more == null) {
            more = "";
        }
        StringBuilder buffer = new StringBuilder(path);
        if (!path.endsWith("/")) {
            buffer.append('/');
        }
        buffer.append(more);

        return buffer.toString();
    }

    /**
     * 确认一个目录存在，如果不存在，则尝试创建此目录。
     *
     * @param path
     *            目录的全路径名
     * @return 如果目录存在，则返回 true，如果无法创建此目录，则返回 false.
     */
    public static boolean makeSurePathExists(String path) {
        File file = new File(path);
        return makeSurePathExists(file);
    }

    /** @see Utils#makeSurePathExists(String) */
    public static boolean makeSurePathExists(File path) {
        return makeSurePathExists(path, false);
    }

    /**
     * 确认一个目录存在，如果不存在，则尝试创建此目录。
     * @param path 路径全路径名
     * @param delete 如果path是文件而不是文件夹，是否删除这个文件，创建文件夹
     * @return true:存在目录或创建成功 false:创建失败
     */
    public static boolean makeSurePathExists(File path, boolean delete) {
        if (path == null) {
            return false;
        }
        if (path.isDirectory()) {
            return true;
        }

        if (!path.exists()) {
            return path.mkdirs();
        } else {
            // 删除文件，创建文件夹
            if (delete) {
                if (!path.delete()) {
                    return false;
                }
                return path.mkdirs();
            }
            // 存在，但是上面的 isDirectory() 返回了 false，说明这是一个已经存在的文件，不是目录
            return false;
        }
    }


    /****************** FILE IO ******************/

    public static final int MAX_FILE_SIZE_TO_GET_MD5 = 10 * 1024 * 1024;

    /**
     * 读取源文件字符数组
     *
     * @param File
     *            file 获取字符数组的文件
     * @return 字符数组
     */
    public static byte[] readFileByte(File file) {
        FileInputStream fis = null;
        FileChannel fc = null;
        byte[] data = null;
        try {
            fis = new FileInputStream(file);
            fc = fis.getChannel();
            data = new byte[(int) (fc.size())];
            fc.read(ByteBuffer.wrap(data));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                    //ignore
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
        return data;
    }

    /**
     * 字符数组写入文件
     *
     * @param byte[] bytes 被写入的字符数组
     * @param File
     *            file 被写入的文件
     * @return 字符数组
     */
    public static boolean writeByteFile(byte[] bytes, File file) {
        if (bytes == null) {
            return false;
        }

        boolean flag = false;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(bytes);
            flag = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            if (fos != null) {
                try {
                    fos.flush();
                } catch (Exception e) {
                    //ignore
                }
                try {
                    fos.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
        return flag;
    }

    // copy a file from srcFile to destFile, return true if succeed, return
    // false if fail
    public static boolean copyFile(File srcFile, File destFile) {
        boolean result = false;
        if (srcFile != null && srcFile.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(srcFile);
                result = copyToFile(in, destFile);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ex) {
                        //ignore
                    }
                }
            }
        }
        return result;
    }

    /**
     * Copy data from a source stream to destFile. Return true if succeed,
     * Return true if succeed, return false if failed.
     */
    public static boolean copyToFile(InputStream inputStream, File destFile) {
        boolean result = false;
        OutputStream out = null;
        try {
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            } else if (destFile.exists()) {
                destFile.delete();
            }
            out = new FileOutputStream(destFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
            }

            result = true;
        } catch (Exception e) {
            result = false;
        } finally {
            if (out != null) {
                try {
                    out.flush();
                } catch (Exception e) {
                    result = false;
                }
                try {
                    out.close();
                } catch (Exception e) {
                    result = false;
                }
            }
        }
        if (!result) {
            destFile.delete();
        }
        return result;
    }

    /**
     * 删除目录以及子目录
     *
     * @param filepath
     *            要删除的目录地址
     * @throws IOException
     */
    public static void deleteDir(String filepath) {
        File f = new File(filepath);
        if (f.exists() && f.isDirectory()) {
            File[] delFiles = f.listFiles();
            if (delFiles != null) {
                if (delFiles.length == 0) {
                    f.delete();
                } else {
                    for (File delFile : delFiles) {
                        if (delFile != null) {
                            if (delFile.isDirectory()) {
                                deleteDir(delFile.getAbsolutePath());
                            }
                            delFile.delete();
                        }
                    }
                    f.delete();
                }
            }
        }
    }

    public static void deleteFile(String filepath) {
        File file = new File(filepath);
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] delFiles = file.listFiles();
                if (delFiles != null) {
                    for (File delFile : delFiles) {
                        if (delFile != null) {
                            deleteFile(delFile.getAbsolutePath());
                        }
                    }
                }
            }
            file.delete();
        }
    }

    public static boolean safeRenameTo(File src, File dst) {
        if (!dst.getParentFile().exists()) {
            dst.getParentFile().mkdirs();
        }
        boolean ret = src.renameTo(dst);
        if (!ret) {
            ret = copyFile(src, dst);
            if (ret) {
                if (src.isDirectory()) {
                    try {
                        deleteDir(src.getAbsolutePath());
                    } catch (Exception e) {
                        //ignore
                    }
                } else {
                    src.delete();
                }
            }
        }
        return ret;
    }

    public static long countDirSize(File aDir) {
        long ret = 0L;
        if (aDir != null && aDir.isDirectory()) {
            File[] files = aDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file != null) {
                        if (file.isDirectory()) {
                            ret += countDirSize(file);
                        } else {
                            ret += file.length();
                        }
                    }
                }
            }
        }
        return ret;
    }

    public static void deleteFile(File file) {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                File[] delFiles = file.listFiles();
                if (delFiles != null) {
                    for (File delFile : delFiles) {
                        deleteFile(delFile);
                    }
                }
            }
            file.delete();
        }
    }

    /**
     * @Synopsis 复制整个文件夹，如果文件夹不存在则创建
     *
     * @Param oldPath
     * @Param newPath
     *
     * @Returns
     */
    public static boolean copyFolder(String oldPath, String newPath) {
        try {
            File newFolder = new File(newPath);
            if (!newFolder.exists()) {
                newFolder.mkdirs(); //如果文件夹不存在 则建立新文件夹
            }
            File oldFolder = new File(oldPath);
            String[] file = oldFolder.list();
            File temp = null;
            if (file != null) {
                for (String fileName : file) {
                    if (oldPath.endsWith(File.separator)) {
                        temp = new File(oldPath + fileName);
                    } else {
                        temp = new File(oldPath + File.separator + fileName);
                    }

                    if (temp.isFile()) {
                        FileInputStream input = new FileInputStream(temp);
                        FileOutputStream output = new FileOutputStream(newPath + "/" + (temp.getName()).toString());
                        byte[] b = new byte[1024 * 5];
                        int len;
                        while ((len = input.read(b)) != -1) {

                            output.write(b, 0, len);
                        }
                        output.flush();
                        output.close();
                        input.close();
                    }
                    if (temp.isDirectory()) { //如果是子文件夹
                        copyFolder(oldPath + "/" + fileName, newPath + "/" + fileName);
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * 防止媒体扫描该文件夹
     * @param file 在此目录下建立.nomedia文件
     */
    public static void createNoMediaFile(File file) {
        if (file == null) {
            return;
        }

        if (!file.exists() || !file.isDirectory()) {
            return;
        }

        /** 创建.nomedia文件，不让媒体扫描 */
        File noMedia = new File(file, ".nomedia");
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
