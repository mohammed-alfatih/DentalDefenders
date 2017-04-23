package objects;

import engine.Engine;
import environment.Environment;
import environment.Obstacle;
import environment.PathFollower;
import processing.core.PApplet;
import processing.core.PVector;
import utility.GameConstants;
import utility.Utility;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by bash on 4/17/2017.
 */


@SuppressWarnings("Duplicates")

public class AIplayer extends GameObject {


    private PApplet app;

    //TODO remove debugging prints

    private enum STATUS {
        IDLE,
        AVOIDING_OBSTACLE,
        DEFENDING_TOOTH,
        SHOOTING_BACK
    }

    private enum PRIORITY {
        TOOTH,
        PLAYER
    }

    public PathFollower pathFollower;


    //TWEAKABLE PARAMETERS
    public static float GREEN_ZONE;
    public static float YELLOW_ZONE,RED_ZONE;
    private static long bulletInterval = 500;


    /* Player properties */
    public static PVector color = new PVector(109, 69, 1);
    public static float size = 20;
    private static float DEFAULT_X = GameConstants.SCR_WIDTH/2 + 90;
    private static float DEFAULT_Y = GameConstants.SCR_HEIGHT/2 + 90;
    private static PVector DEFAULT_POS = new PVector(DEFAULT_X,DEFAULT_Y);
    private int patrolTargetIterator = 3;
    private static PVector[] PatrolTargets = {new PVector(DEFAULT_X,DEFAULT_Y), new PVector(GameConstants.SCR_WIDTH/2 + 90,GameConstants.SCR_HEIGHT/2 - 90), new PVector(GameConstants.SCR_WIDTH/2 - 90,GameConstants.SCR_HEIGHT/2 - 90), new PVector(GameConstants.SCR_WIDTH/2 - 90, GameConstants.SCR_HEIGHT/2 + 90)};
    private static float DEFAULT_ORIENTATION = 0;
    private static final int DEFAULT_PLAYER_LIFE = 100;
    private static float DEFAULT_PLAYER_MAXVEL = 1f;
    private static STATUS status;
    private static PRIORITY priority;
    private static boolean followingPath;
    private static PRIORITY currPriorityAsset;
    private static float shootingOffset = 2f;
    private static Enemy enemycurrentlyHighestPriority;
    private long lastBulletTime;



    public Set<Bullet> bullets;
    public int bulletCount = 0;
    public int enemiesKilled = 0;

    public PVector playerTarget;
    public static float BulletDamage = 10;


    public AIplayer(PApplet app)
    {
        super(app, color, size, DEFAULT_X, DEFAULT_Y, DEFAULT_ORIENTATION, DEFAULT_PLAYER_LIFE);
        this.app = app;
        status = STATUS.IDLE;
        currPriorityAsset = PRIORITY.PLAYER;
        YELLOW_ZONE = 300f;
        RED_ZONE = 200f;
        setMaxVel(DEFAULT_PLAYER_MAXVEL);
        bullets = new HashSet<>();
        playerTarget = new PVector(DEFAULT_X, DEFAULT_Y);

        pathFollower = new PathFollower(this, Environment.numTiles, Environment.tileSize);

    }


    public void shoot() {
        long now = System.currentTimeMillis();
        if(now-lastBulletTime >= bulletInterval)
        {
            bullets.add(new Bullet(app, getPosition(), getOrientation(), GameConstants.DEFAULT_BULLET_SIZE, color, Bullet.Origin.ENEMY));
            lastBulletTime = now;
        }
    }

    public void update(){

        //updating the position and orientation of the player

        Align(playerTarget);
        Arrive(playerTarget);

        super.update();
        updateBullets();

    }

    public void updateBullets(){
        for (Iterator<Bullet> i = bullets.iterator(); i.hasNext(); ) {
            Bullet b = i.next();
            boolean bulletRemoved = false;

            for(Iterator<Enemy> j = Engine.Enemies.iterator(); j.hasNext(); ){
                Enemy e = j.next();
                if(b.hasHit(e))
                {
                    i.remove();
                    bulletRemoved = true;
                    e.takeDamage(AIplayer.BulletDamage);

                    if(e.getLife()<=0)
                    {
                        j.remove();
                    }
                    break;
                }
            }

            if(bulletRemoved){
                break;
            }

            if (b.outOfBounds()) {
                i.remove();
                bulletRemoved = true;
            }
            else
                b.update();
        }
    }


    public void setStatus()
    {
        //player should, according to different parameters, perform an action. this method sets the state of the player.
        if ((float) Engine.tooth.tooth.getLife() / (float) Engine.tooth.life <= 0.25f){
            currPriorityAsset = PRIORITY.TOOTH;
        }else{
            if((float) this.getLife() / (float) DEFAULT_PLAYER_LIFE <= 0.25f)
                currPriorityAsset = PRIORITY.PLAYER;
        }

        enemycurrentlyHighestPriority = getEnemyWithHighestPriority();
        if(enemycurrentlyHighestPriority != null) {
            app.ellipse(enemycurrentlyHighestPriority.position.x, enemycurrentlyHighestPriority.position.y, 50, 50);
            playerTarget = enemycurrentlyHighestPriority.position;
        }

        if(checkObstacle()){
            status = STATUS.AVOIDING_OBSTACLE;
        }else {
            if (enemycurrentlyHighestPriority == null) {
                //System.out.println("No enemy priority :/");
                status = STATUS.IDLE;
            }else{
                status = STATUS.SHOOTING_BACK;
            }
        }
    }

    public void behaviour(){
        //depending on the current status of the player, perform a different action
        setStatus();
        System.out.println(status);

        if (followingPath && !pathFollower.reachedTarget)
        {
            pathFollower.followPath();
        }

        else if (followingPath)
            followingPath = false;

        switch (status)
        {
            case AVOIDING_OBSTACLE:
                //Align(playerTarget);
                //Arrive(playerTarget);
                avoidObstacle();
                break;
            case DEFENDING_TOOTH:
                followingPath = false;
                //Align(playerTarget);
                //Arrive(playerTarget);
                defendTooth();
                break;
            case SHOOTING_BACK:
                followingPath = false;
                //Align(playerTarget);
                //Arrive(playerTarget);
                shootingBack();
                break;
            case IDLE:
                followingPath = false;
                //Wander();
                //TODO the player should patrol the tooth, but for some reason movement is not working.
                defaultBehavior();
                break;

        }
    }


    public void defaultBehavior(){
        //if the status is idle, return to default location.
        System.out.println("Patrolling..");
        if(this.getPosition().dist(PatrolTargets[patrolTargetIterator]) <= 0.1f) {
            patrolTargetIterator = (patrolTargetIterator + 1) % 4;
            System.out.print("Next Target ");

            updateTarget(PatrolTargets[patrolTargetIterator]);
        }
        update();
    }

    public void updateTarget(PVector updatedTarget) {
        // this method is called frequently to update the final target
        if (!Environment.invalidNodes.contains(Utility.getGridIndex(new PVector(updatedTarget.x, updatedTarget.y))))
            playerTarget.set(updatedTarget.x, updatedTarget.y);
    }

    public boolean checkObstacle(){
        // this method checks if an obstacle appears in front of the player, then delegates to avoidObstacle method if one is found.
        if(this.velocity.mag() != 0 && obstacleCollisionDetected())
            return true;
        return false;
    }

    public void avoidObstacle(){
        //path follow to target
        try {
            updateTarget(enemycurrentlyHighestPriority.position);
        }catch(NullPointerException e){
            //updateTarget(DEFAULT_POS);
            System.out.println("enemy is null");
        }

        //TODO - I have absolutely no idea why this still throws an exception - Kinshuk
        try {
            pathFollower.findPath(getGridLocation(), Utility.getGridLocation(playerTarget));
            followingPath = true;
            pathFollower.followPath();
        }catch(NullPointerException e){
            System.out.println("Null pointer exception bro");
        }
    }


    public void defendTooth(){
        // if not LOS, then get LOS and shoot at enemy
    }
    //TODO player for some reason moves while attacking enemies.
    public void shootingBack(){
        // probably has LOS, but if not then get LOS and shoot at enemy
        this.stopMoving();
        if(enemycurrentlyHighestPriority.getLife() > 0) {
            if(hasLOS(enemycurrentlyHighestPriority.getPosition())) {
                followingPath = false;
                PVector shootingPoint = PVector.add(enemycurrentlyHighestPriority.getPosition(), PVector.mult(enemycurrentlyHighestPriority.getVelocity(), shootingOffset));
                PVector dir = PVector.sub(shootingPoint, this.getPosition());
                this.setOrientation(dir.heading());
                shoot();
            }else{
                //TODO - Add the get to LOS code here
                System.out.println(getGridLocation());
                System.out.println(enemycurrentlyHighestPriority.getGridLocation());
                /*
                pathFollower.findPath(getGridLocation(), Utility.getGridLocation(enemycurrentlyHighestPriority.position));
                followingPath = true;
                pathFollower.followPath();
                */
            }
        }
    }

    public Enemy getEnemyWithHighestPriority() {
        float highestPrioritySoFar = 0;
        Enemy enemyWithHighestPriority = null;

        //Trying to stick to the last enemy if it's still alive and in the yellow zone
        /*
        if(enemycurrentlyHighestPriority != null && enemycurrentlyHighestPriority.life>0
                && enemycurrentlyHighestPriority.position.dist(Engine.tooth.tooth.position)<=YELLOW_ZONE)
        {
            return enemycurrentlyHighestPriority;
        }
        */

        for (Iterator<Enemy> j = Engine.Enemies.iterator(); j.hasNext(); ) {
            Enemy e = j.next();
            //uncomment next two line if we want to set an enemy with highest priority even when none are in the danger zone
//            if(e.enemyPriority > highestPrioritySoFar)
//                enemyWithHighestPriority = e;
            if(e.getClass().getSimpleName().toString().compareTo("Enemy_enemelator") == 0){
                return e;
            }
            if(e.position.dist(Engine.tooth.tooth.getPosition()) <= YELLOW_ZONE) {
                if(e.enemyPriority < 10) {
                    e.enemyPriority += 10;
                }
                if (highestPrioritySoFar < e.enemyPriority) {
                    highestPrioritySoFar = e.enemyPriority;
                    enemyWithHighestPriority = e;
                }
            }else{
                if(e.enemyPriority > 10)
                    e.enemyPriority -= 10;
            }
        }
        return enemyWithHighestPriority;
    }

    //Overrode this method to avoid tooth collisions- may be a temporary fix
    public boolean obstacleCollisionDetected(){
        lookAheadPosition = PVector.fromAngle(velocity.heading()).mult(lookAheadDistance);

        lookAheadPosition.x += position.x;
        lookAheadPosition.y += position.y;

        for (Obstacle o : Engine.staticObjects)
            if (o.contains(Utility.getGridLocation(lookAheadPosition)))
                return true;

        return false;
    }



}
