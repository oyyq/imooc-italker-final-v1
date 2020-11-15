package net.qiujuer.italker.factory.model.db.typeConverter;


import com.raizlabs.android.dbflow.converter.TypeConverter;

import net.qiujuer.italker.factory.model.api.SysNotify.NonStateModel;

/**
 * 将Model内部非数据库类型(Model)的字段 的值转化成在数据库可以定义的类型 todo 利用类型转换器TypeConverter
 * TypeConverter还定义了当一个Model被加载时, 使用TypeConverter重新创建字段
 * todo TypeConverter只能转化成常规列, PrimaryKey 或ForeignKey是不能转化的
 *      被注解定义的转换器在所有DataBase共享
 * 参考: https://www.bookstack.cn/read/DBFlow_CN/usage-TypeConverters.md
 */
// First type param is the type that goes into the database
// Second type param is the type that the model contains for that field.
@com.raizlabs.android.dbflow.annotation.TypeConverter
public class NSModelConverter extends TypeConverter<String, NonStateModel> {


    @Override
    public String getDBValue(NonStateModel model) {
        return model == null ?null: model.getUserId()+","+model.getGroupId();
    }

    @Override
    public NonStateModel getModelValue(String data) {
        if(data == null) return null;
        String[] values = data.split(",");
        if(values.length < 2){
            return null;
        }else {
            NonStateModel nsmodel = new NonStateModel();
            nsmodel.setUserId(values[0]);
            nsmodel.setGroupId(values[1]);
            return nsmodel;
        }
    }
}
