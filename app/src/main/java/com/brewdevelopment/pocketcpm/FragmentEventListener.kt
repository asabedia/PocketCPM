package com.brewdevelopment.pocketcpm

/**
 * Created by ashkanabedian on 2017-08-21.
 */
interface FragmentEventListener {
    fun onAdd(obj:Any)
    fun onEdit(obj:Any)       //needs to have an id that is > -1
    fun onProjectSelect(obj: Any)
    fun onTaskSelect(obj: Any)

}