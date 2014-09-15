package edu.buffalo.cse.blueseal.blueseal;

import soot.Unit;
import soot.jimple.InvokeStmt;

public class BSStmntSwitch {
	private Unit unit_ = null;
	public BSStmntSwitch(Unit unit) {
		// TODO Auto-generated constructor stub
		this.unit_ = unit;
	}

  public Boolean isInvokeStmnt(){

    if(this.unit_ instanceof InvokeStmt){
      return true;
    }
    return false;
  }
}
