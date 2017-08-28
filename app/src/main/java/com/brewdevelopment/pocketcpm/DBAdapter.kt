package com.brewdevelopment.pocketcpm

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import java.sql.SQLException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by neyonlime on 2017-08-16.
 */
class DBAdapter(dbName: String, context: Context){

    private var db: SQLiteDatabase
    companion object {

        private lateinit var mDBManager: com.brewdevelopment.pocketcpm.DBAdapter.DBManager

        val READ = "read"               //only for reading
        val WRITE= "write"              //for reading and writing
        private val EMPTY: Long = -1

        //debugging
        val PROJECT_ADDED = "project_add"
    }
    
    init {
        mDBManager = DBManager(context, "pocketcpm.db")        //instance of database manager
        db = mDBManager.writableDatabase
    }

    //used to open the database

    @Throws(SQLException::class)
    fun open(){
        //open the data base
        if(!db.isOpen){
            db = mDBManager.writableDatabase
        }
    }


    //ASYNC TASK
    /*
    private class OpenDatabaseTask: AsyncTask<String, Boolean, Boolean>() {
        override fun doInBackground(vararg params: String?): Boolean {
            for(request in params){
                if(request == com.brewdevelopment.pocketcpm.DBAdapter.WRITE){
                    //get readable database
                    db = mDBManager.writableDatabase
                    if(db.isOpen){
                        return true // was opened
                    }

                }else if(request == com.brewdevelopment.pocketcpm.DBAdapter.READ){
                    //get writeable database
                    db = mDBManager.readableDatabase
                    if(db.isOpen){
                       return true //was opened
                    }
                }
            }
            return false    //was not opened
        }

        override fun onPostExecute(result: Boolean?) {
            super.onPostExecute(result)
        }
    }*/

    //deletes the specified table
    fun deleteTable(tbName: String){
        val SQL_DELETE_ENTRIES: String =
                "DROP TABLE IF EXISTS " + tbName;
    }

    fun checkDBState(){
        if(db === null || !db.isOpen){
            throw IllegalStateException("Database is not open")
        }
    }

    //save to database
    fun save(obj: Any){ //takes in any object, only performs functions with Project, Task, and Champion obj
        when(obj){
            is Project -> {
                //save a project
                //open(WRITE)             //opens the database
                checkDBState()          //checks that the database is open and ready for print

                if(obj.ID != EMPTY){
                    //METHOD 1:
                    //if project already exists
                    //get the current version of the project
                    //and compare with the version that has been passed in
                    //and construct a projection

                    //METHOD 2:
                    //just update the existing version with all the info of the new version
                    //whether it is different or not

                    //METHOD 2...
                    update(obj)

                }

                var values = ContentValues()
                var taskList = ""
                for(task in obj.taskList){
                    taskList+=","+task.ID
                    Log.i(PROJECT_ADDED, taskList)
                }

                if(taskList.length > 0){
                    taskList = taskList.substring(1)
                }

                values.put(DBManager.Contract.ProjectTable.NAME_COLUMN, obj.name)
                values.put(DBManager.Contract.ProjectTable.TASK_LIST_COLUMN, taskList)
                values.put(DBManager.Contract.ProjectTable.TOTAL_TIME_COLUMN, obj.getTotalTime())
                obj.ID = db.insert(DBManager.Contract.ProjectTable.TABLE_NAME, null, values)
            }
            is Task -> {
                //save task
                //open(WRITE)
                checkDBState()

                Log.d("save_task", "task saved!")

                var values = obj.attribute
                obj.ID = db.insert(DBManager.Contract.TaskTable.TABLE_NAME, null,values)

            }
            is Champion -> {
                //save champion
                //open(WRITE)
                checkDBState()
                var taskList = ""
                for(task in obj.assignedTasks){
                    taskList += "," + task.ID               //id1,id2,id3, ...
                }
                if(taskList.length > 1){
                    taskList = taskList.substring(1)
                }

                var values = ContentValues()
                values.put(DBManager.Contract.ChampionTable.NAME_COLUMN, obj.name)
                values.put(DBManager.Contract.ChampionTable.TASKS_COLUMN, taskList)

                obj.ID = db.insert(DBManager.Contract.ChampionTable.TABLE_NAME,null,values)
            }
            else -> {}
        }
    }
    fun close(){
        if(db !== null && db.isOpen) db.close()
    }


    //Appends a task to the respective project
    fun saveTask(project: Project, task: Task){
        //open(WRITE)
        checkDBState()


        if(task.ID != EMPTY){
            //update the task information
            //the id is already added to a project so that doesnt ned to be updated unless the task is deleted
            Log.d("add_task", "updating: Task: ${task.attribute.get(Task.NAME_COLUMN)} to Project: ${project.name}, ${project.ID}")
            update(task)
            return
        }

        Log.d("add_task", "adding: Task: ${task.attribute.get(Task.NAME_COLUMN)} to Project: ${project.name}, ${project.ID}")

        //save task to TaskTable
        var taskValues = task.attribute
        task.ID = db.insert(DBManager.Contract.TaskTable.TABLE_NAME,null,taskValues)

        Log.d("add_task", "task: ${task.ID} added! size: ${DatabaseUtils.queryNumEntries(db, DBManager.Contract.TaskTable.TABLE_NAME)}")

        //save the task to the project table
        var taskList = ""
        for(task in project.taskList){
            taskList+=","+task.ID
            Log.i(PROJECT_ADDED, taskList)
        }

        if(taskList.isNotEmpty()){
            taskList = taskList.substring(1)
        }

        Log.d("add_task", "project tasks: ${taskList}||Size: ${project.taskList.size}")

        var projectValues = ContentValues()
        projectValues.put(DBManager.Contract.ProjectTable.TASK_LIST_COLUMN, taskList)

        var selection = DBManager.Contract.ProjectTable.ID + " LIKE ?"
        var selectionArgs = arrayOf(""+project.ID)

        var count = db.update(DBManager.Contract.ProjectTable.TABLE_NAME, projectValues, selection, selectionArgs)
    }

    //Accessors
    fun getProjects(): ArrayList<Project> {
        //open(READ)

        var projections = arrayOf(DBManager.Contract.ProjectTable.ID, DBManager.Contract.ProjectTable.NAME_COLUMN)
        checkDBState()

        var sortOrder = DBManager.Contract.ProjectTable.NAME_COLUMN+ " DESC"
        var cursor: Cursor = db.query(DBManager.Contract.ProjectTable.TABLE_NAME, projections,null,null, null, null, sortOrder)

        var projects: ArrayList<Project> = ArrayList()
        while(cursor.moveToNext()){
            var name: String = cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.ProjectTable.NAME_COLUMN))
            var project = Project(name)

            project.ID = cursor.getLong(cursor.getColumnIndexOrThrow(DBManager.Contract.ProjectTable.ID))
            projects.add(project)
        }
        cursor.close()
        return projects
    }

    //pulls all the tasks from the database
    fun getAllTasks(): ArrayList<Task>{
        var projections = arrayOf(DBManager.Contract.TaskTable.ID, DBManager.Contract.TaskTable.NAME_COLUMN,
                DBManager.Contract.TaskTable.DESCRIPTION_COLUMN, DBManager.Contract.TaskTable.CHAMPION_COLUMN,
                DBManager.Contract.TaskTable.START_COLUMN, DBManager.Contract.TaskTable.END_COLUMN,
                DBManager.Contract.TaskTable.PREDECESSOR_COLUMN, DBManager.Contract.TaskTable.DEPENDENT_COLUMN)

        var sortOrder = DBManager.Contract.TaskTable.NAME_COLUMN + " DESC"
        var cursor: Cursor = db.query(DBManager.Contract.TaskTable.TABLE_NAME, projections, null, null, null, null, sortOrder)

        var taskList = ArrayList<Task>()
        while(cursor.moveToNext()){
            var task = Task()
            task.ID = cursor.getLong(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.ID))
            task.attribute.put(Task.NAME_COLUMN,cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.NAME_COLUMN)))
            task.attribute.put(Task.DESCRIPTION_COLUMN, cursor.getString((cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.DESCRIPTION_COLUMN))))
            task.attribute.put(Task.CHAMPION_COLUMN,cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.CHAMPION_COLUMN)))
            task.attribute.put(Task.START_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.START_COLUMN)))
            task.attribute.put(Task.END_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.END_COLUMN)))
            task.attribute.put(Task.PREDECESSOR_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.PREDECESSOR_COLUMN)))
            task.attribute.put(Task.DEPENDENT_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.DEPENDENT_COLUMN)))
            taskList.add(task)
        }
        return taskList
    }

    fun getTaskList(id: Long): ArrayList<Task> {
        //open(READ)

        var projections = arrayOf(DBManager.Contract.ProjectTable.ID, DBManager.Contract.ProjectTable.TASK_LIST_COLUMN)
        checkDBState()
        var selection = DBManager.Contract.ProjectTable.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())

        var sortOrder = DBManager.Contract.ProjectTable.ID + " DESC"

        var cursor: Cursor = db.query(DBManager.Contract.ProjectTable.TABLE_NAME, projections, selection, selectionArgs, null, null, sortOrder)

        var taskIds = ""
        while(cursor.moveToNext()){
            //should only loop once
            taskIds = cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.ProjectTable.TASK_LIST_COLUMN))
        }
        cursor.close()
        var ids = taskIds.split(',')       //array of the ids (String)

        Log.d("add_task", "ids: ${ids.size}")

        var taskList = ArrayList<Task>()
        for(id in ids){
            var temp = getTaskById(id)
            if(temp !== null){
                taskList.add(temp)
            }
        }

        return taskList
    }

    //gets a project form the database given the id
    fun getProjectByID(ID: Long): Project{
        //search database for the current project and return it
        var projections = arrayOf(DBManager.Contract.ProjectTable.ID, DBManager.Contract.ProjectTable.NAME_COLUMN, DBManager.Contract.ProjectTable.TASK_LIST_COLUMN)
        var selection = DBManager.Contract.ProjectTable.ID + " = ?"
        var selectionArgs = arrayOf(ID.toString())

        var sortOrder = DBManager.Contract.ProjectTable.ID + " DESC"

        var cursor: Cursor = db.query(DBManager.Contract.ProjectTable.TABLE_NAME, projections, selection, selectionArgs, null, null, sortOrder)

        var project = Project()
        while(cursor.moveToNext()){
            var name = cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.ProjectTable.NAME_COLUMN))
            var ID = cursor.getLong(cursor.getColumnIndexOrThrow(DBManager.Contract.ProjectTable.ID))
            project.taskList = getTaskList(project.ID)
            project.name = name
            project.ID = ID
        }
        return project
    }

    //gets a task from the database givin the ID
    fun getTaskById(id: String): Task?{
        checkDBState()



        var projections = arrayOf(DBManager.Contract.TaskTable.ID, DBManager.Contract.TaskTable.NAME_COLUMN,
                DBManager.Contract.TaskTable.DESCRIPTION_COLUMN, DBManager.Contract.TaskTable.CHAMPION_COLUMN,
                DBManager.Contract.TaskTable.START_COLUMN, DBManager.Contract.TaskTable.END_COLUMN,
                DBManager.Contract.TaskTable.PREDECESSOR_COLUMN, DBManager.Contract.TaskTable.DEPENDENT_COLUMN)

        var selection = DBManager.Contract.TaskTable.ID + " = ?"
        var selectionArgs = arrayOf(id)

        var sortOrder = DBManager.Contract.TaskTable.NAME_COLUMN + " DESC"


        var cursor: Cursor = db.query(DBManager.Contract.TaskTable.TABLE_NAME, projections, selection, selectionArgs, null, null, sortOrder)

        var task = Task()
        while(cursor.moveToNext()){
            task.ID = cursor.getLong(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.ID))
            task.attribute.put(Task.NAME_COLUMN,cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.NAME_COLUMN)))
            task.attribute.put(Task.DESCRIPTION_COLUMN, cursor.getString((cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.DESCRIPTION_COLUMN))))
            task.attribute.put(Task.CHAMPION_COLUMN,cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.CHAMPION_COLUMN)))
            task.attribute.put(Task.START_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.START_COLUMN)))
            task.attribute.put(Task.END_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.END_COLUMN)))
            task.attribute.put(Task.PREDECESSOR_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.PREDECESSOR_COLUMN)))
            task.attribute.put(Task.DEPENDENT_COLUMN, cursor.getString(cursor.getColumnIndexOrThrow(DBManager.Contract.TaskTable.DEPENDENT_COLUMN)))
        }

        if(task.ID != EMPTY){
            return task
        }else{
            return null
        }
    }

    //gets a champion from the database given the id
    fun getChampionByID(ID: Long){

    }

    //helper
    //updates the instance of an element in the database with a new object
    //containg the same idea but some different information
    fun update (obj: Any){
        //map out the new values
        when(obj){
            is Project -> {
                var values = ContentValues()
                values.put(DBManager.Contract.ProjectTable.NAME_COLUMN, obj.name)
                values.put(DBManager.Contract.ProjectTable.TOTAL_TIME_COLUMN, obj.getTotalTime())

                var taskList = ""
                for(task in obj.taskList){
                    taskList+=","+task.ID
                }

                if(taskList.length > 1){
                    taskList = taskList.substring(1)
                }
                values.put(DBManager.Contract.ProjectTable.TASK_LIST_COLUMN, taskList)

                var selection = "${DBManager.Contract.ProjectTable.NAME_COLUMN}=? and " +
                        "${DBManager.Contract.ProjectTable.TASK_LIST_COLUMN}=? and " +
                        "${DBManager.Contract.ProjectTable.TOTAL_TIME_COLUMN}=?"
                var selectionArgs = arrayOf(DBManager.Contract.ProjectTable.NAME_COLUMN,
                                            DBManager.Contract.ProjectTable.TASK_LIST_COLUMN,
                                            DBManager.Contract.ProjectTable.TOTAL_TIME_COLUMN)

                //updates the current version of the project will the attributes of the passed in project
                var count = db.update(DBManager.Contract.ProjectTable.TABLE_NAME, values, selection, selectionArgs)
            }
            is Task -> {
                var values = obj.attribute      //sets the attributes of the task that is going to be safe
                var selection = "${DBManager.Contract.TaskTable.NAME_COLUMN}=? and ${DBManager.Contract.TaskTable.DESCRIPTION_COLUMN}=? and " +
                        "${DBManager.Contract.TaskTable.CHAMPION_COLUMN}=? and " +
                        "${DBManager.Contract.TaskTable.START_COLUMN}=? and " +
                        "${DBManager.Contract.TaskTable.END_COLUMN}=? and " +
                        "${DBManager.Contract.TaskTable.PREDECESSOR_COLUMN}=? and ${DBManager.Contract.TaskTable.DEPENDENT_COLUMN}=?"

                var selectionArgs = arrayOf(DBManager.Contract.TaskTable.NAME_COLUMN, DBManager.Contract.TaskTable.DESCRIPTION_COLUMN,
                                            DBManager.Contract.TaskTable.CHAMPION_COLUMN, DBManager.Contract.TaskTable.START_COLUMN,
                                            DBManager.Contract.TaskTable.END_COLUMN, DBManager.Contract.TaskTable.PREDECESSOR_COLUMN,
                                            DBManager.Contract.TaskTable.DEPENDENT_COLUMN)
                var count = db.update(DBManager.Contract.TaskTable.TABLE_NAME, values, selection, selectionArgs) //update the task
            }
            is Champion -> {
                var values = ContentValues()
                values.put(DBManager.Contract.ChampionTable.NAME_COLUMN, obj.name)

                //build the task list
                var taskList=""
                for(task in obj.assignedTasks){
                    taskList += "," + task.ID
                }
                if(taskList.length > 1){
                    taskList = taskList.substring(1)
                }

                values.put(DBManager.Contract.ChampionTable.TASKS_COLUMN, taskList)

                var selection = "${DBManager.Contract.ChampionTable.NAME_COLUMN}=? and ${DBManager.Contract.ChampionTable.TASKS_COLUMN}=?"
                var selectionArgs = arrayOf(DBManager.Contract.ChampionTable.NAME_COLUMN, DBManager.Contract.ChampionTable.TASKS_COLUMN)

                var count = db.update(DBManager.Contract.ChampionTable.TABLE_NAME, values, selection, selectionArgs)
            }
        }
    }


    private class DBManager(context: Context, dbName: String): SQLiteOpenHelper(context, dbName, null, VERSION){

        //schema definition
        companion object Contract {
            var VERSION = 1   //must be incremented if the schema is changed

            object ProjectTable: BaseColumns {
                val TABLE_NAME = "projects"
                val ID: String = "_id"
                val NAME_COLUMN = "name"
                val TASK_LIST_COLUMN = "tasklist"
                val TOTAL_TIME_COLUMN = "totaltime"
            }

            object TaskTable{
                val TABLE_NAME = "tasks"
                val ID: String = "_id"
                val NAME_COLUMN = "name"
                val DESCRIPTION_COLUMN = "description"
                val CHAMPION_COLUMN = "champion"
                val START_COLUMN = "start"
                val END_COLUMN = "end"
                val PREDECESSOR_COLUMN = "predecessor"
                val DEPENDENT_COLUMN = "dependent"
            }

            object ChampionTable{
                val TABLE_NAME = "champions"
                val ID: String = "_id"
                val NAME_COLUMN = "name"
                val TASKS_COLUMN = "tasks"

            }
        }


        override fun onCreate(db: SQLiteDatabase?) {
            //create the table in the respective method 'createProjectTable()'
            //automatically creates a tasks table and a projects table  and a champion table
            createTable(db, ProjectTable.TABLE_NAME)
            createTable(db, TaskTable.TABLE_NAME)
            createTable(db, ChampionTable.TABLE_NAME)
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            //define an ubgrade policy in the case that a new version is pushed
            //this is offline and maintains no links so just recreaete the database
            TODO("Implement the upgrade method")
        }

        //called to create the project table
        fun createTable(db: SQLiteDatabase?, table: String){
            when (table){
                ProjectTable.TABLE_NAME -> {
                    val CREATE_SQL_ENTERIES = "CREATE TABLE IF  NOT EXISTS ${ProjectTable.TABLE_NAME}(" +
                            "${ProjectTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT, ${ProjectTable.NAME_COLUMN} TEXT," +
                            "${ProjectTable.TASK_LIST_COLUMN} TEXT, ${ProjectTable.TOTAL_TIME_COLUMN} FLOAT)"
                    db!!.execSQL(CREATE_SQL_ENTERIES)
                }
                TaskTable.TABLE_NAME -> {
                    val CREATE_SQL_ENTERIES = "CREATE TABLE IF  NOT EXISTS ${TaskTable.TABLE_NAME}(" +
                            "${TaskTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT, ${TaskTable.NAME_COLUMN} TEXT," +
                            "${TaskTable.DESCRIPTION_COLUMN} TEXT," +
                            "${TaskTable.CHAMPION_COLUMN} TEXT, ${TaskTable.START_COLUMN} TEXT," +
                            "${TaskTable.END_COLUMN} TEXT, ${TaskTable.PREDECESSOR_COLUMN} TEXT," +
                            "${TaskTable.DEPENDENT_COLUMN} TEXT)"
                    db!!.execSQL(CREATE_SQL_ENTERIES)
                }

                ChampionTable.TABLE_NAME -> {
                    val CREATE_SQL_ENTERIES = "CREATE TABLE IF  NOT EXISTS ${ChampionTable.TABLE_NAME} (" +
                            "${ChampionTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT, ${ChampionTable.NAME_COLUMN} TEXT, " +
                            "${ChampionTable.TASKS_COLUMN} TEXT)"
                    db!!.execSQL(CREATE_SQL_ENTERIES)
                }
            }
        }
    }

}